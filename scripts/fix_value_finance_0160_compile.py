from pathlib import Path

root = Path(__file__).resolve().parents[1]
path = root / "app/src/main/java/br/com/mapeiaia/rotacerta/FinancialActivity.kt"
source = path.read_text(encoding="utf-8")
old = """    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }
"""
new = """    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }
"""
if old not in source:
    raise SystemExit("0.1.160 nullable onNewIntent anchor not found")
path.write_text(source.replace(old, new, 1), encoding="utf-8")
print("0.1.160: FinancialActivity onNewIntent signature corrected")
