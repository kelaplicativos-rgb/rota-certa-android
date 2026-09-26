from pathlib import Path

SOURCE = Path("app/src/main/java/br/com/mapeiaia/rotacerta/trips/PassengerTimelineUi.kt")
BUILD = Path("app/build.gradle.kts")
MARKER = "// 0.1.285: restored compact per-passenger shortcuts from the proven pre-regression row."

text = SOURCE.read_text()
if MARKER in text:
    raise SystemExit("0.1.285 passenger shortcuts already materialized")

name_anchor = '''                TextButton(
                    onClick = {
                        val externalTarget = externalPassengerTarget(passenger)
'''
if text.count(name_anchor) != 1:
    raise SystemExit(f"unexpected passenger-name anchor count: {text.count(name_anchor)}")

trip_shortcut = '''                // 0.1.285: restored compact per-passenger shortcuts from the proven pre-regression row.
                externalTripTarget(entry.blablaProfileUuid, entry.blablaTripHref)?.let {
                    IconButton(
                        onClick = {
                            if (!openExternalTripBlaBla(context, entry.blablaProfileUuid, entry.blablaTripHref)) {
                                Toast.makeText(
                                    context,
                                    "Conta BlaBlaCar desta viagem não está conectada.",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_blablacar_action),
                            contentDescription = "Abrir viagem no BlaBlaCar",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

'''
text = text.replace(name_anchor, trip_shortcut + name_anchor, 1)

message_block = '''                OutlinedButton(
                    onClick = { copyPassengerConfirmationMessage(context, entry, passenger) },
                    contentPadding = COMPACT_ACTION_PADDING,
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Text("💬", maxLines = 1)
                }
'''
if text.count(message_block) != 1:
    raise SystemExit(f"unexpected confirmation-button count: {text.count(message_block)}")

money_and_message = '''                OutlinedButton(
                    onClick = {
                        if (passenger.fareMinorUnits != null) {
                            copyPassengerFareValue(context, passenger)
                        } else {
                            fareEditRow = passenger
                        }
                    },
                    contentPadding = COMPACT_ACTION_PADDING,
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Text("💰", maxLines = 1)
                }

''' + message_block
text = text.replace(message_block, money_and_message, 1)
SOURCE.write_text(text)

build = BUILD.read_text()
if 'versionCode = 5577' not in build or 'versionName = "0.1.284"' not in build:
    raise SystemExit("unexpected 0.1.284 version baseline")
build = build.replace('versionCode = 5577', 'versionCode = 5578', 1)
build = build.replace('versionName = "0.1.284"', 'versionName = "0.1.285"', 1)
BUILD.write_text(build)
