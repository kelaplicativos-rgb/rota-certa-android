from pathlib import Path
import subprocess

path = Path("app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt")
text = path.read_text(encoding="utf-8")

start_marker = "@Composable\nprivate fun AboutRotaCertaCard() {"
end_marker = "\n\n@Composable\nprivate fun SavedPlacesCard("

start = text.find(start_marker)
if start < 0:
    raise SystemExit("AboutRotaCertaCard start marker not found")
end = text.find(end_marker, start)
if end < 0:
    raise SystemExit("AboutRotaCertaCard end marker not found")
if text.find(start_marker, start + 1) >= 0:
    raise SystemExit("More than one AboutRotaCertaCard definition found")

replacement = """@Composable
private fun AboutRotaCertaCard() {
    br.com.mapeiaia.rotacerta.versioncenter.VersionCenterCard()
}"""

updated = text[:start] + replacement + text[end:]
if updated == text:
    raise SystemExit("No change produced")

path.write_text(updated, encoding="utf-8")
print("Materialized VersionCenterCard into AboutRotaCertaCard")

# Persist the already-proven surgical integration before Gradle reaches the
# repository-external google-services.json requirement. The final source commit
# also removes this one-shot materializer and its one-shot workflow.
subprocess.run(["git", "diff", "--check"], check=True)
changed = subprocess.check_output(["git", "diff", "--name-only"], text=True).splitlines()
if changed != ["app/src/main/java/br/com/mapeiaia/rotacerta/MainActivity.kt"]:
    raise SystemExit(f"Unexpected materializer changes: {changed}")

subprocess.run(["git", "config", "user.name", "github-actions[bot]"], check=True)
subprocess.run(
    ["git", "config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com"],
    check=True,
)
subprocess.run(["git", "add", str(path)], check=True)
subprocess.run(
    [
        "git",
        "rm",
        ".github/scripts/version_center_0550_materialize.py",
        ".github/workflows/version-center-0550-finalize.yml",
    ],
    check=True,
)
subprocess.run(["git", "diff", "--cached", "--check"], check=True)
subprocess.run(
    ["git", "commit", "-m", "feat(version-center): integrate About center 0.1.550"],
    check=True,
)
subprocess.run(
    ["git", "push", "origin", "HEAD:agent/version-center-0.1.550"],
    check=True,
)
print("Persisted Version Center integration and removed temporary machinery")
