"""Баланс и расход OpenRouter (карточка 018). Печатает одну строку: <usage> <remaining>; с --text — текст для людей.
usage — накопленный расход ключа в $ (для подсчёта стоимости карточки), remaining — остаток в $
(меньшее из остатка счёта и лимита ключа). Неизвестное значение — «-». Код выхода всегда 0."""
import json, os, urllib.request


def get(path, key):
    req = urllib.request.Request("https://openrouter.ai/api/v1/" + path, headers={"Authorization": f"Bearer {key}"})
    return json.load(urllib.request.urlopen(req, timeout=20)).get("data", {})


def details():
    """(usage ключа, остаток счёта, остаток лимита ключа) — None, если неизвестно."""
    key = os.environ.get("OPENROUTER_API_KEY", "")
    usage = account = keyleft = None
    if not key:
        return usage, account, keyleft
    try:
        d = get("key", key)
        usage = d.get("usage")
        if d.get("limit_remaining") is not None:
            keyleft = float(d["limit_remaining"])
    except Exception:
        pass
    try:
        c = get("credits", key)
        account = float(c["total_credits"]) - float(c["total_usage"])
    except Exception:
        pass
    return usage, account, keyleft


def balance():
    """(usage, доступно сейчас) — доступно = меньшее из остатка счёта и лимита ключа."""
    usage, account, keyleft = details()
    left = [v for v in (account, keyleft) if v is not None]
    return usage, (min(left) if left else None)


def describe():
    _, account, keyleft = details()
    parts = []
    if account is not None:
        parts.append(f"счёт ${account:.2f}")
    if keyleft is not None:
        parts.append(f"лимит ключа: осталось ${keyleft:.2f}")
    return ", ".join(parts) or "—"


if __name__ == "__main__":
    import sys
    if "--text" in sys.argv:
        print(describe()); raise SystemExit(0)
    u, r = balance()
    print(f"{'-' if u is None else f'{float(u):.4f}'} {'-' if r is None else f'{r:.2f}'}")
