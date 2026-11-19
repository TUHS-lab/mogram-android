"""Проверка ресурсов Mogram без сборки APK.

1. Файлы строк (values и values-ru) читаются как XML и не содержат повторов имён.
2. Каждое имя Mogram-строки (R.string.MogramXxx и getString("MogramXxx", ...)) из Java есть в values/strings.xml.
3. Каждая Mogram-строка из values-ru есть и в values (иначе русский вариант без английского).
4. Нет маркеров конфликта слияния в исходниках.
Запуск: python tests/check_resources.py (из корня репозитория). Код выхода 1 при ошибках.
"""
import collections
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

root = pathlib.Path(__file__).resolve().parent.parent
res = root / "TMessagesProj" / "src" / "main" / "res"
errors = []


def names_of(path):
    try:
        tree = ET.parse(path)
    except ET.ParseError as e:
        errors.append("%s: не XML: %s" % (path, e))
        return []
    return [e.get("name") for e in tree.getroot().findall("string")]


base = names_of(res / "values" / "strings.xml")
ru = names_of(res / "values-ru" / "strings.xml")
for label, names in (("values", base), ("values-ru", ru)):
    for name, count in collections.Counter(names).items():
        if count > 1:
            errors.append("%s: повтор строки %s" % (label, name))

base_set = set(base)
for name in ru:
    if name.startswith("Mogram") and name not in base_set:
        errors.append("values-ru: нет английской строки %s" % name)

used = collections.defaultdict(set)
pattern_r = re.compile(r"R\.string\.(Mogram\w+)")
pattern_s = re.compile(r'getString\(\s*"(Mogram\w+)"')
conflict = re.compile(r"^(<<<<<<<|>>>>>>>) ", re.M)
for path in (root / "TMessagesProj").rglob("*.java"):
    text = path.read_text(encoding="utf-8", errors="replace")
    if conflict.search(text):
        errors.append("%s: маркер конфликта слияния" % path.relative_to(root))
    for match in pattern_r.finditer(text):
        used[match.group(1)].add(path.name)
    for match in pattern_s.finditer(text):
        used[match.group(1)].add(path.name)
for path in (root / "TMessagesProj_AppStandalone").rglob("*.java"):
    text = path.read_text(encoding="utf-8", errors="replace")
    for match in pattern_r.finditer(text):
        used[match.group(1)].add(path.name)

for name, files in sorted(used.items()):
    if name not in base_set:
        errors.append("нет строки %s (нужна в %s)" % (name, ", ".join(sorted(files))))

for path in (root / "TMessagesProj").rglob("*.xml"):
    if "res" in path.parts and path.stat().st_size < 2_000_000:
        try:
            ET.parse(path)
        except ET.ParseError as e:
            errors.append("%s: не XML: %s" % (path.relative_to(root), e))

print("Строк values: %d, values-ru: %d, используемых Mogram-имён: %d" % (len(base), len(ru), len(used)))
if errors:
    for line in errors:
        print("ОШИБКА", line)
    sys.exit(1)
print("Ресурсы в порядке")
