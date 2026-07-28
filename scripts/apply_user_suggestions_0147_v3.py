from pathlib import Path

source = Path(__file__).with_name("apply_user_suggestions_0147_v2.py").read_text()
source = source.replace('Bolinha e aparência', 'Bolinha e aparencia')
exec(compile(source, "apply_user_suggestions_0147_v3.py", "exec"))
