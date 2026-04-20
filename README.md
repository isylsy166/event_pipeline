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
처음에는 이벤트를 단일 테이블에 저장하는 방식을 검토했습니다. 하지만 `page_view`, `purchase`, `error`를 한 테이블에 넣으면 타입별 고유 필드가 서로에게 nullable 컬럼이 되고, 이벤트 타입이 추가될 때마다 테이블 전체에 ALTER가 필요한 구조가 됩니다.
그래서 공통 필드는 `events`에 두고 타입별 필드는 서브테이블로 분리하는 Table-Per-Type 패턴을 선택했습니다. 집계 쿼리 대부분이 `events` 테이블만 참조하도록 설계해, 서브테이블 JOIN이 필요한 경우를 최소화했습니다.

### 2. 트랜잭션 처리
`events` 삽입은 성공했지만 서브테이블 삽입이 실패하면, 어떤 타입인지 알 수 없는 고아 레코드(orphaned record)가 남습니다. 이런 데이터는 집계 결과를 오염시키고 원인 추적도 어렵게 만듭니다.
두 INSERT를 하나의 트랜잭션으로 묶어 원자성을 보장하고, 실패 시 롤백해 부분 저장이 발생하지 않도록 처리했습니다.

### 3. 현실적인 데이터 생성
균등 분포로 생성한 데이터는 시간대별 추이 같은 분석 결과가 평평하게 나와서, 차트를 그려도 의미있는 패턴이 보이지 않습니다.
실제 서비스 패턴을 반영해 아래 규칙으로 데이터를 생성했습니다.

- **이벤트 비율**: PAGE_VIEW 70% / PURCHASE 20% / ERROR 10%
- **시간대 가중치**: 이벤트 타입별, 평일/주말별로 피크타임을 다르게 설정 (예: 구매는 점심·퇴근 후, 에러는 트래픽이 몰리는 시간대에 집중)
- **날짜 분포**: 최근 날짜일수록 이벤트가 더 많이 몰리도록 가중치 적용 (오늘 35%, 어제 25%, 이후 점감)

이 덕분에 시간대별 추이 차트에서 실제 서비스와 유사한 피크 패턴이 나타납니다.

### 4. 커넥션 풀(HikariCP)
이벤트를 1건씩 INSERT할 때마다 커넥션을 새로 열면 TCP 핸드셰이크 비용이 매번 발생합니다. 1000건 기준으로는 체감이 크지 않지만, 커넥션 관리를 애플리케이션 레벨에서 직접 하는 구조는 실제 서비스에서 쓰지 않는 방식입니다.
HikariCP를 적용해 커넥션을 재사용하도록 했습니다. 동시 요청이 늘어나더라도 풀 크기 내에서 제어되고, DB 입장에서도 커넥션 수가 예측 가능해집니다.

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
