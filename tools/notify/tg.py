"""Отправка сообщения в Telegram (карточка 017). Только стандартная библиотека.
Использование: python3 -I tools/notify/tg.py "текст"   или   ... | python3 -I tools/notify/tg.py -
Нужны TELEGRAM_BOT_TOKEN и TELEGRAM_CHAT_ID; без них — молча пропуск. Никогда не роняет вызывающего (код 0)."""
import json, os, sys, time, urllib.request

LIMIT = 4000


def send(text):
    token = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip()
    chat = os.environ.get("TELEGRAM_CHAT_ID", "").strip()
    if not token or not chat:
        print("Telegram не настроен, пропуск")
        return False
    text = (text or "").strip() or "(пусто)"
    if len(text) > LIMIT:
        text = text[:LIMIT] + "\n…(обрезано)"
    data = json.dumps({"chat_id": chat, "text": text, "disable_web_page_preview": True}).encode()
    for attempt in (1, 2):
        try:
            req = urllib.request.Request(f"https://api.telegram.org/bot{token}/sendMessage", data=data,
                                         headers={"Content-Type": "application/json"})
            urllib.request.urlopen(req, timeout=20).read()
            return True
        except Exception as e:  # сеть, 4xx/5xx — печатаем и не падаем
            print(f"Telegram: ошибка отправки (попытка {attempt}): {e}")
            time.sleep(3)
    return False


if __name__ == "__main__":
    arg = sys.argv[1] if len(sys.argv) > 1 else "-"
    send(sys.stdin.read() if arg == "-" else " ".join(sys.argv[1:]))
    sys.exit(0)
