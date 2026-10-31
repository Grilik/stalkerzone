"""Ревью PR другой моделью: карточка + .clinerules + diff -> OpenRouter -> markdown в stdout."""
import glob, json, os, re, subprocess, urllib.request

def sh(*a):
    return subprocess.run(a, capture_output=True, text=True, errors="replace").stdout

base = os.environ.get("BASE_SHA", "origin/main")
ref = os.environ.get("HEAD_REF", "")
files = [f for f in sh("git", "diff", "--name-only", base + "...HEAD").splitlines() if f]

# Механические проверки — без модели
bad = [f for f in files if re.match(r"^(bin|build|out|run)/", f) or re.search(r"\.(log|err|class|szl|dds)$", f)
       or (("/" not in f) and f.endswith(".txt"))]
hard = []
if bad:
    hard.append("Лишние файлы в PR (вывод IDE/сборки, логи, данные игры): " + ", ".join(bad[:15]) + (" …" if len(bad) > 15 else ""))
for f in files:
    if re.match(r"^src/main/java/ru/stalkerzone/(sim|zonemap)/.*\.java$", f) and os.path.exists(f):
        if re.search(r"^import\s+net\.(minecraft|minecraftforge)\.", open(f, encoding="utf-8", errors="replace").read(), re.M):
            hard.append(f"`{f}`: импорт Minecraft/Forge в sim/zonemap запрещён")
for f in ("build.gradle", "gradle.properties", "gradle/wrapper/gradle-wrapper.properties"):
    if f in files:
        hard.append(f"Изменён `{f}` — запрещено без отдельной карточки")

m = re.search(r"task/(\d{3})", ref)
card = ""
if m:
    c = glob.glob(f"docs/tasks/{m.group(1)}-*.md")
    if c:
        card = open(c[0], encoding="utf-8").read()
rules = open(".clinerules", encoding="utf-8").read() if os.path.exists(".clinerules") else ""
code_files = [f for f in files if f not in bad]
diff = sh("git", "diff", "-U3", base + "...HEAD", "--", *code_files) if code_files else ""
LIMIT = 150000
cut = len(diff) > LIMIT
diff = diff[:LIMIT]

out = ["## 🤖 Автоматическое ревью"]
if hard:
    out.append("### Блокеры (механическая проверка)")
    out += [f"- {h}" for h in hard]

key = os.environ.get("OPENROUTER_API_KEY")
model = os.environ.get("REVIEW_MODEL") or "z-ai/glm-5.3"
if not key:
    out.append("_Секрет OPENROUTER_API_KEY не задан — ревью моделью пропущено._")
elif not diff:
    out.append("_Нет изменений кода._")
else:
    prompt = f"""Ты ревьюер мода Minecraft (Forge 1.20.1, Java 17). Исполнитель (ИИ-агент) сделал PR по карточке задачи.
Проверь строго по карточке и правилам. Не хвали, не пересказывай diff. Пиши по-русски, кратко.

Ответь в формате:
**Вердикт:** ОК / ЕСТЬ ЗАМЕЧАНИЯ / БЛОКЕР
**Пункты «Готово, когда»:** по каждому — выполнен / не видно / нарушен (одной строкой)
**Проблемы:** список (файл:место — что не так — почему важно). Только реальные: нарушение «Не делать», правил,
ошибки логики, сломанная совместимость сохранений (версия не поднята, чтение старых версий), enum не в конец,
деньги/товары мимо Ledger, тест не проверяет то, что заявлено, мёртвый/отладочный код. Если проблем нет — «нет».

# Карточка
{card or '(карточка не найдена по имени ветки)'}

# Правила проекта (.clinerules)
{rules}

# Описание PR от исполнителя
{os.environ.get('PR_BODY','')[:4000]}

# Diff{' (обрезан)' if cut else ''}
{diff}
"""
    def ask(m):
        # рассуждающие модели тратят токены на размышления: даём запас и просим короткое рассуждение
        req = urllib.request.Request("https://openrouter.ai/api/v1/chat/completions",
            data=json.dumps({"model": m, "max_tokens": 12000, "temperature": 0.1, "reasoning": {"effort": "low"},
                             "messages": [{"role": "user", "content": prompt}]}).encode(),
            headers={"Authorization": "Bearer " + key, "Content-Type": "application/json",
                     "X-Title": "stalkerzone-ai-review"})
        r = json.load(urllib.request.urlopen(req, timeout=300))
        text = ((r.get("choices") or [{}])[0].get("message") or {}).get("content") or ""
        return text.strip(), r.get("usage", {})
    text, u, err = "", {}, ""
    for m in (model, os.environ.get("REVIEW_FALLBACK_MODEL") or "deepseek/deepseek-v4.1-flash"):
        try:
            text, u = ask(m)
            if text:
                model = m
                break
            err = f"{m}: пустой ответ"
        except Exception as e:
            err = f"{m}: {e}"
    if text:
        out.append(text)
        out.append(f"\n_Модель: `{model}`, токены: {u.get('prompt_tokens','?')} → {u.get('completion_tokens','?')}{', diff обрезан' if cut else ''}._")
    else:
        # маркер для конвейера: сбой ревью — не повод звать старшего
        out.append(f"_Ревью моделью не удалось ({err}). REVIEW_UNAVAILABLE_")
print("\n\n".join(out))
