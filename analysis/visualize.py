import psycopg2
import matplotlib.pyplot as plt
import matplotlib.ticker as mticker
import matplotlib.font_manager as fm
import os

# 나눔고딕 폰트 적용 (Docker 컨테이너에 fonts-nanum 설치된 경우)
_nanum = [f.fname for f in fm.fontManager.ttflist if "Nanum" in f.name]
if _nanum:
    plt.rcParams["font.family"] = fm.FontProperties(fname=_nanum[0]).get_name()
plt.rcParams["axes.unicode_minus"] = False

DB_CONFIG = {
    "host": os.environ.get("DB_HOST", "localhost"),
    "port": 5432,
    "dbname": "event_pipeline",
    "user": "pipeline",
    "password": "pipeline123",
}

OUTPUT_DIR = os.path.dirname(os.path.abspath(__file__))


def get_connection():
    return psycopg2.connect(**DB_CONFIG)


def fetch(query):
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(query)
            columns = [desc[0] for desc in cur.description]
            rows = cur.fetchall()
    return columns, rows


def save(fig, filename):
    path = os.path.join(OUTPUT_DIR, filename)
    fig.savefig(path, bbox_inches="tight", dpi=150)
    plt.close(fig)
    print(f"Saved: {path}")


# 1. 이벤트 타입별 발생 횟수
def plot_event_type_count():
    _, rows = fetch("""
        SELECT event_type, COUNT(*) AS event_count
        FROM events
        GROUP BY event_type
        ORDER BY event_count DESC
    """)
    labels = [r[0] for r in rows]
    counts = [r[1] for r in rows]

    fig, ax = plt.subplots(figsize=(7, 5))
    bars = ax.bar(labels, counts, color=["#4C72B0", "#55A868", "#C44E52"])
    ax.bar_label(bars, padding=4)
    ax.set_title("이벤트 타입별 발생 횟수")
    ax.set_xlabel("Event Type")
    ax.set_ylabel("Count")
    ax.yaxis.set_major_locator(mticker.MaxNLocator(integer=True))
    save(fig, "1_event_type_count.png")


# 2. 유저별 총 이벤트 수
def plot_user_event_count():
    _, rows = fetch("""
        SELECT user_id, COUNT(*) AS event_count
        FROM events
        GROUP BY user_id
        ORDER BY event_count DESC
    """)
    labels = [r[0] for r in rows]
    counts = [r[1] for r in rows]

    fig, ax = plt.subplots(figsize=(10, 5))
    bars = ax.bar(labels, counts, color="#4C72B0")
    ax.bar_label(bars, padding=4)
    ax.set_title("유저별 총 이벤트 수")
    ax.set_xlabel("User ID")
    ax.set_ylabel("Count")
    ax.tick_params(axis="x", rotation=45)
    ax.yaxis.set_major_locator(mticker.MaxNLocator(integer=True))
    save(fig, "2_user_event_count.png")


# 3. 시간대별 이벤트 추이
def plot_hourly_trend():
    _, rows = fetch("""
        SELECT DATE_TRUNC('hour', timestamp) AS hour, COUNT(*) AS event_count
        FROM events
        GROUP BY hour
        ORDER BY hour
    """)
    labels = [r[0].strftime("%H:%M") for r in rows]
    counts = [r[1] for r in rows]

    fig, ax = plt.subplots(figsize=(10, 5))
    ax.plot(labels, counts, marker="o", color="#4C72B0", linewidth=2)
    ax.fill_between(labels, counts, alpha=0.2, color="#4C72B0")
    ax.set_title("시간대별 이벤트 추이")
    ax.set_xlabel("Hour")
    ax.set_ylabel("Count")
    ax.tick_params(axis="x", rotation=45)
    ax.yaxis.set_major_locator(mticker.MaxNLocator(integer=True))
    save(fig, "3_hourly_trend.png")


# 4. 에러 이벤트 비율
def plot_error_rate():
    _, rows = fetch("""
        SELECT event_type, COUNT(*) AS cnt
        FROM events
        GROUP BY event_type
    """)
    labels = [r[0] for r in rows]
    counts = [r[1] for r in rows]
    colors = ["#C44E52" if l == "ERROR" else "#AEC6CF" for l in labels]
    explode = [0.05 if l == "ERROR" else 0 for l in labels]

    fig, ax = plt.subplots(figsize=(6, 6))
    ax.pie(counts, labels=labels, colors=colors, explode=explode,
           autopct="%1.1f%%", startangle=140)
    ax.set_title("에러 이벤트 비율")
    save(fig, "4_error_rate.png")


# 5. 상품별 총 매출
def plot_product_revenue():
    _, rows = fetch("""
        SELECT product_id, SUM(amount) AS total_revenue
        FROM purchase_events
        GROUP BY product_id
        ORDER BY total_revenue DESC
    """)
    labels = [r[0] for r in rows]
    revenues = [float(r[1]) for r in rows]

    fig, ax = plt.subplots(figsize=(8, 5))
    bars = ax.bar(labels, revenues, color="#55A868")
    ax.bar_label(bars, fmt="₩%.0f", padding=4, fontsize=9)
    ax.set_title("상품별 총 매출")
    ax.set_xlabel("Product ID")
    ax.set_ylabel("Total Revenue (₩)")
    ax.yaxis.set_major_formatter(mticker.FuncFormatter(lambda x, _: f"₩{x:,.0f}"))
    save(fig, "5_product_revenue.png")


if __name__ == "__main__":
    plot_event_type_count()
    plot_user_event_count()
    plot_hourly_trend()
    plot_error_rate()
    plot_product_revenue()
    print("All charts saved.")
