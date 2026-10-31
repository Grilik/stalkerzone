"""Баланс и расход OpenRouter (карточка 018). Печатает одну строку: <usage> <remaining>
usage — накопленный расход ключа в $ (для подсчёта стоимости карточки), remaining — остаток в $
(меньшее из остатка счёта и лимита ключа). Неизвестное значение — «-». Код выхода всегда 0."""
import json, os, urllib.request


def get(path, key):
    req = urllib.request.Request("https://openrouter.ai/api/v1/" + path, headers={"Authorization": f"Bearer {key}"})
    return json.load(urllib.request.urlopen(req, timeout=20)).get("data", {})


def balance():
    key = os.environ.get("OPENROUTER_API_KEY", "")
    usage = remaining = None
    if not key:
        return usage, remaining
    left = []
    try:
        d = get("key", key)
        usage = d.get("usage")
        if d.get("limit_remaining") is not None:
            left.append(float(d["limit_remaining"]))
    except Exception:
        pass
    try:
        c = get("credits", key)
        left.append(float(c["total_credits"]) - float(c["total_usage"]))
    except Exception:
        pass
    return usage, (min(left) if left else None)


if __name__ == "__main__":
    u, r = balance()
    print(f"{'-' if u is None else f'{float(u):.4f}'} {'-' if r is None else f'{r:.2f}'}")
