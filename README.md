## 1. 실행 방법
``` hcl
⏺ # 1. 전체 스택 실행 (이벤트 생성 + 저장 + 차트 생성까지 자동)                                                                                                                                                                          
  docker compose up --build                                                                                                                                                                                                              
                                                                                                                                                                                                                                         
  # 2. DB에 데이터가 잘 저장됐는지 확인                                                                                                                                                                                                  
  docker exec -it event_pipeline_db psql -U pipeline -d event_pipeline -c "SELECT event_type, COUNT(*) FROM events GROUP BY event_type;"                                                                                                 
                                                                                                                                                                                                                                         
  # 3. 분석 쿼리 실행                                                                                                                                                                                                                    
  docker exec -i event_pipeline_db psql -U pipeline -d event_pipeline < analysis/queries.sql                                                                                                                               
                                                                                                                                                                                                                                         
  # 4. 생성된 차트 확인
  open analysis/1_event_type_count.png                                                                                                                                                                                                   
  open analysis/2_user_event_count.png                                                                                                                                                                                                 
  open analysis/3_hourly_trend.png                                                                                                                                                                                                       
  open analysis/4_error_rate.png                                                                                                                                                                                                       
  open analysis/5_product_revenue.png                                                                                                                                                                                                    
                                                                                                                                                                                                                                         
  # 5. 재실행 시 (볼륨 초기화 후 처음부터)
  docker compose down -v && docker compose up --build      
```

## 2. 스키마 설명
- 이벤트 공통 필드(`user_id`, `session_id`, `timestamp` 등)는 `events` 테이블 하나에 모아 타입별 집계 쿼리를 단순하게 유지했습니다.
- 타입별 고유 필드(`page_url`, `amount`, `error_code` 등)는 `page_view_events`, `purchase_events`, `error_events` 서브테이블로 분리해, 단일 테이블에 nullable 컬럼이 넘치는 구조를 피했습니다.
- 서브테이블의 PK를 `events.id`를 참조하는 FK로 설정해 1:1 관계를 보장하고, `ON DELETE CASCADE`로 데이터 정합성을 유지했습니다.

### DB 선택 이유 (PostgreSQL)
- 이벤트 타입별 서브테이블 구조를 쓰기 때문에 FK, CASCADE, 트랜잭션 등 관계형 제약 조건을 온전히 활용할 수 있는 RDBMS가 적합했습니다.
- SQLite는 단일 파일 기반이라 컨테이너 환경에서 볼륨 마운트 없이는 데이터가 유지되지 않고, 동시 쓰기에도 제약이 있습니다.
- MySQL 대비 PostgreSQL은 `RETURNING` 절을 지원해 INSERT 후 생성된 `id`를 별도 SELECT 없이 바로 받아올 수 있어, 서브테이블 저장 시 코드가 단순해집니다.

## 3. 구현하면서 고민한 점
### 1. 데이터 모델링
처음에는 이벤트를 단일 테이블에 저장하는 방식을 검토했습니다. 
하지만 `page_view`, `purchase`, `error`를 한 테이블에 넣으면 타입별 고유 필드가 서로에게 nullable 컬럼이 되고, 
이벤트 타입이 추가될 때마다 테이블 전체에 ALTER가 필요한 구조가 됩니다.
그래서 공통 필드는 `events`에 두고 타입별 필드는 서브테이블로 분리하는 Table-Per-Type 패턴을 선택했습니다. 
집계 쿼리 대부분이 `events` 테이블만 참조하도록 설계해, 서브테이블 JOIN이 필요한 경우를 최소화했습니다.

### 2. 트랜잭션 처리
`events` 삽입은 성공했지만 서브테이블 삽입이 실패하면, 어떤 타입인지 알 수 없는 고아 레코드(orphaned record)가 남습니다. 
이런 데이터는 집계 결과를 오염시키고 원인 추적도 어렵게 만듭니다.
두 INSERT를 하나의 트랜잭션으로 묶어 원자성을 보장하고, 실패 시 롤백해 부분 저장이 발생하지 않도록 처리했습니다.

### 3. 현실적인 데이터 생성
균등 분포로 생성한 데이터는 시간대별 추이 같은 분석 결과가 평평하게 나와서, 차트를 그려도 의미있는 패턴이 보이지 않습니다.
실제 서비스 패턴을 반영해 아래 규칙으로 데이터를 생성했습니다.

- **이벤트 비율**: PAGE_VIEW 70% / PURCHASE 20% / ERROR 10%
- **시간대 가중치**: 이벤트 타입별, 평일/주말별로 피크타임을 다르게 설정 (예: 구매는 점심·퇴근 후, 에러는 트래픽이 몰리는 시간대에 집중)
- **날짜 분포**: 최근 날짜일수록 이벤트가 더 많이 몰리도록 가중치 적용 (오늘 35%, 어제 25%, 이후 점감)

이 덕분에 시간대별 추이 차트에서 실제 서비스와 유사한 피크 패턴이 나타납니다.

### 4. 커넥션 풀(HikariCP)
이벤트를 1건씩 INSERT할 때마다 커넥션을 새로 열면 TCP 핸드셰이크 비용이 매번 발생합니다. 
1000건 기준으로는 체감이 크지 않지만, 커넥션 관리를 애플리케이션 레벨에서 직접 하는 구조는 실제 서비스에서 쓰지 않는 방식입니다.
HikariCP를 적용해 커넥션을 재사용하도록 했습니다. 
동시 요청이 늘어나더라도 풀 크기 내에서 제어되고, DB 입장에서도 커넥션 수가 예측 가능해집니다.

### 5. 개선하면 좋다고 생각되는 것들
- 지금은 1건씩 INSERT하고 있어서, 데이터가 더 많아지면 배치 처리로 바꾸는 게 먼저일 것 같습니다.
- 현재는 생성하고 바로 DB에 넣는 구조인데, 실제 서비스라면 중간에 `앱 → Kafka → Consumer → DB` 와 같이 큐를 두는 방식이 이벤트 유실 없이 처리 가능해서 이 구조도 고민해볼 수 있다고 생각합니다.
- 조회가 많아지면 `timestamp`, `user_id`, `event_type` 쪽에는 인덱스를 추가해야 할 것 같습니다.

## 4. 패키지 구조
```
event_pipeline/
├── src/main/java/
│   ├── Main.java                      # 실행 진입점 (이벤트 1000건 생성 및 저장)
│   ├── model/
│   │   ├── EventType.java             # 이벤트 타입 enum (PAGE_VIEW, PURCHASE, ERROR)
│   │   ├── Event.java                 # 공통 필드 추상 클래스
│   │   ├── PageViewEvent.java         # 페이지 조회 이벤트
│   │   ├── PurchaseEvent.java         # 구매 이벤트
│   │   └── ErrorEvent.java            # 에러 이벤트
│   ├── generator/
│   │   └── EventGenerator.java        # 랜덤 이벤트 생성기
│   ├── repository/
│   │   └── EventRepository.java       # DB 저장 (events + 서브테이블 트랜잭션)
│   └── config/
│       └── DatabaseConfig.java        # HikariCP 커넥션 풀 설정
├── src/main/resources/
│   └── application.yml                # DB 연결 정보
├── db/
│   └── schema.sql                     # 테이블 정의 (컨테이너 시작 시 자동 실행)
├── analysis/
│   ├── queries.sql                    # 집계 분석 쿼리 5개
│   ├── visualize.py                   # 차트 생성 스크립트 (matplotlib)
│   └── Dockerfile                     # 시각화용 Python 이미지 (한글 폰트 포함)
├── Dockerfile                         # Java 앱 멀티스테이지 빌드
└── docker-compose.yml                 # postgres → app → visualizer 순 실행
```

## 5. AWS 아키텍처 설계
이 프로젝트를 AWS에서 운영한다면, 실제 서비스처럼 외부 클라이언트의 API 요청을 받아 이벤트를 수집하고 저장하는 구조로 확장할 수 있습니다.

![Infra Architecture.jpeg](Infra%20Architecture.jpeg)

### 서비스 선택과 이유
- `VPC (Public / Private Subnet 분리)`: ALB는 외부 트래픽을 받아야 하므로 Public Subnet에 두고, Fargate와 RDS는 외부에서 직접 접근할 수 없는 Private Subnet에 격리했습니다. DB가 인터넷에 노출되지 않아 보안 측면에서 기본적인 네트워크 격리를 확보할 수 있습니다.
- `ALB(Application Load Balancer)`: 클라이언트 요청을 받아 Fargate 컨테이너로 분산합니다. Fargate 태스크가 여러 개로 스케일 아웃되더라도 ALB가 트래픽을 고르게 나눠주고, HTTPS 종료 지점 역할도 합니다.
- `ECS Fargate`: 이미 Dockerfile 기반으로 앱을 구성했기 때문에 컨테이너 이미지를 그대로 배포할 수 있습니다. EC2 인스턴스를 직접 관리하지 않아도 돼 운영 복잡도가 낮고, 트래픽에 따라 태스크 수를 늘리거나 줄이기도 쉽습니다.
- `RDS for PostgreSQL`: 현재 스키마가 관계형 모델(`events` + 타입별 서브테이블, FK, CASCADE, 인덱스)에 맞춰 설계되어 있습니다. 로컬 Docker Compose의 PostgreSQL을 관리형 DB로 옮기는 구조라 설계 연속성이 자연스럽습니다.
- `S3`: 차트 이미지처럼 한 번 생성된 결과 파일을 보관하는 용도입니다. 정형 데이터가 아니라 파일 보관소 역할이기 때문에 RDS가 아닌 객체 스토리지로 분리하는 편이 역할이 명확합니다.
- `CloudWatch`: Fargate 태스크의 로그, 에러, 실행 현황을 한 곳에서 확인할 수 있습니다. 애플리케이션 코드 변경 없이 ECS와 자동 연동됩니다.
- `ECR (Elastic Container Registry)`: Java 앱의 Docker 이미지를 저장하고 버전 관리하는 컨테이너 레지스트리입니다. Fargate 태스크 시작 시 ECR에서 이미지를 pull하기 때문에, 배포할 이미지를 보관할 저장소가 필요합니다. AWS 내부 네트워크에서 pull하므로 외부 레지스트리(Docker Hub 등)보다 빠르고, IAM 기반 접근 제어도 적용됩니다.

### 서비스 역할 차이
- `ALB`는 외부 트래픽의 진입점입니다. 직접 비즈니스 로직을 수행하지 않고, 요청을 Fargate 태스크로 라우팅하는 역할만 합니다.
- `ECS Fargate`는 실제 코드가 실행되는 곳입니다. 이벤트 수집 API를 처리하고 RDS에 저장하며, 차트 생성 후 S3에 업로드합니다.
- `ECR`은 컨테이너 이미지 저장소입니다. 애플리케이션 코드 자체가 아니라 빌드된 이미지를 보관하며, Fargate가 배포 시 참조합니다.
- `RDS`는 구조화된 원본 이벤트 데이터의 저장소입니다. SQL 집계, JOIN, 인덱스 활용이 필요한 데이터베이스 역할을 합니다.
- `S3`는 시각화 산출물(PNG 파일 등)을 보관하는 파일 저장소입니다. 쿼리가 필요한 데이터가 아니라 완성된 결과물을 두는 곳이라 RDS와 역할이 다릅니다.
- `CloudWatch`는 저장소가 아닌 관찰 도구입니다. 로그, 오류, 메트릭을 추적하는 운영용 서비스입니다.

### 이 아키텍처에서 가장 고민한 부분
가장 고민한 지점은 Fargate와 RDS를 같은 Private Subnet에 둘지, 별도 Subnet으로 분리할지였습니다.
실제 운영 환경에서는 RDS를 별도의 DB 전용 Subnet(DB Subnet Group)에 두고 Fargate에서만 접근을 허용하는 Security Group을 구성하는 방식이 더 세밀한 격리를 제공합니다.
다만 이번 설계에서는 구조의 단순함을 우선해 하나의 Private Subnet으로 묶었습니다.
또 하나 고민한 부분은 차트 시각화 결과를 RDS에 둘지, S3로 분리할지였습니다.
차트 이미지는 SQL로 재조회가 필요한 정형 데이터가 아니라 한 번 생성된 결과 파일이기 때문에,
원본 이벤트는 RDS, 시각화 산출물은 S3로 분리하는 쪽이 역할 분리가 명확하다고 판단했습니다.