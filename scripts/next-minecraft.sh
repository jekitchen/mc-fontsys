#!/usr/bin/env bash
# Tracks the next Minecraft version (PRD D61): the version Fontsys is tested on before its jar
# covers it. The versions live in gradle.properties (next_minecraft_version, next_fabric_api_version,
# next_modmenu_version); this script looks them up and runs the checks. See docs/release-26.4.md.
#
#   scripts/next-minecraft.sh check     Print the newest versions for the next Minecraft and compare
#                                       them with gradle.properties. Exit 0 if they match, 3 if a
#                                       newer build is out.
#   scripts/next-minecraft.sh bump      The same, then write the newest versions into gradle.properties.
#   scripts/next-minecraft.sh compile   Compile the mod and its game tests against the next version
#                                       (shows what a port must change; nothing is kept).
#   scripts/next-minecraft.sh test      Boot the built jar on the next version and run the full
#                                       client game-test suite (./gradlew runNextClientGameTest).
#
# Sources, the same ones the PRD records: Mojang's version manifest; Fabric Maven for Fabric API,
# whose gradle.properties on GitHub names the Minecraft build it targets; Modrinth for Mod Menu;
# NeoForged's Maven for NeoForge. Only versions are fetched; nothing is downloaded or installed.
set -euo pipefail

cd "$(dirname "$0")/.."
PROPS=gradle.properties
# python3 on Linux and macOS; on Windows "python3" may be the Store's placeholder, so try each.
PY=
for candidate in python3 python; do
	if "$candidate" -c 'import sys' >/dev/null 2>&1; then PY=$candidate; break; fi
done
[ -n "$PY" ] || { echo "Needs Python 3" >&2; exit 2; }

prop() { sed -n "s/^$1=//p" "$PROPS" | tr -d '\r'; }

# The release the next version belongs to, e.g. 26.4 for 26.4-snapshot-8 or 26.4.
next=$(prop next_minecraft_version)
line=$(echo "$next" | sed -E 's/^([0-9]+\.[0-9]+).*/\1/')

latest_minecraft() {
	curl -fsS https://piston-meta.mojang.com/mc/game/version_manifest_v2.json | "$PY" -c '
import json, sys
line = sys.argv[1]
for v in json.load(sys.stdin)["versions"]:
    if v["id"] == line or v["id"].startswith(line + ".") or v["id"].startswith(line + "-"):
        print(v["id"], v["type"], v["releaseTime"][:10])
        break
' "$line"
}

latest_fabric_api() {
	curl -fsS https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml \
		| grep -o "<version>[^<]*+${line}</version>" | sed 's/<[^>]*>//g' | tail -1 || true
}

# The Minecraft build a Fabric API version is made for, from its tagged gradle.properties.
fabric_api_target() {
	curl -fsS "https://raw.githubusercontent.com/FabricMC/fabric-api/${1/+/%2B}/gradle.properties" \
		| sed -n 's/^minecraft_version=//p' | tr -d '\r'
}

latest_modmenu() {
	curl -fsS "https://api.modrinth.com/v2/project/modmenu/version?game_versions=%5B%22$1%22%5D" | "$PY" -c '
import json, sys
versions = json.load(sys.stdin)
print(versions[0]["version_number"] if versions else "")
'
}

neoforge_builds() {
	curl -fsS https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml \
		| grep -o "<version>${line}\.[^<]*</version>" | sed 's/<[^>]*>//g' | tail -1 || true
}

lookup() {
	echo "Checked $(date -u +%Y-%m-%d) for Minecraft ${line}:"
	read -r mc mc_type mc_date <<<"$(latest_minecraft)"
	[ -n "$mc" ] || { echo "No Minecraft ${line} in Mojang's version manifest" >&2; exit 1; }
	echo "  Minecraft    ${mc} (${mc_type}, ${mc_date})"
	fapi=$(latest_fabric_api)
	[ -n "$fapi" ] || { echo "No Fabric API for ${line} on Fabric Maven" >&2; exit 1; }
	fapi_mc=$(fabric_api_target "$fapi")
	[ -n "$fapi_mc" ] || { echo "Could not read the Minecraft version Fabric API ${fapi} is built for" >&2; exit 1; }
	echo "  Fabric API   ${fapi} (built for ${fapi_mc})"
	mm=$(latest_modmenu "$fapi_mc")
	echo "  Mod Menu     ${mm:-none for ${fapi_mc}}"
	nf=$(neoforge_builds)
	echo "  NeoForge     ${nf:-none for ${line}}"
	if [ "$fapi_mc" != "$mc" ]; then
		echo "  Note: Fabric API has no build for ${mc} yet; the next version stays at ${fapi_mc}."
	fi
	echo "gradle.properties:"
	echo "  next_minecraft_version=$(prop next_minecraft_version)"
	echo "  next_fabric_api_version=$(prop next_fabric_api_version)"
	echo "  next_modmenu_version=$(prop next_modmenu_version)"
}

set_prop() {
	"$PY" - "$PROPS" "$1" "$2" <<'EOF'
import re, sys
path, key, value = sys.argv[1:]
text = open(path, encoding="utf-8").read()
text, n = re.subn(r"(?m)^" + re.escape(key) + r"=.*$", lambda m: key + "=" + value, text)
if n != 1:
    sys.exit(f"{key} not found once in {path}")
open(path, "w", encoding="utf-8", newline="\n").write(text)
EOF
}

up_to_date() {
	[ "$(prop next_minecraft_version)" = "$fapi_mc" ] \
		&& [ "$(prop next_fabric_api_version)" = "$fapi" ] \
		&& [ -z "$mm" -o "$(prop next_modmenu_version)" = "$mm" ]
}

gradle_next() {
	./gradlew "$@" \
		-Pminecraft_version="$(prop next_minecraft_version)" \
		-Pfabric_api_version="$(prop next_fabric_api_version)" \
		-Pmodmenu_version="$(prop next_modmenu_version)"
}

case "${1:-}" in
	check)
		lookup
		if up_to_date; then echo "Up to date."; else echo "Newer builds are out: scripts/next-minecraft.sh bump"; exit 3; fi
		;;
	bump)
		lookup
		if up_to_date; then echo "Up to date."; exit 0; fi
		# Fabric API decides: a client without a matching Fabric API can't run the tests.
		set_prop next_minecraft_version "$fapi_mc"
		set_prop next_fabric_api_version "$fapi"
		[ -n "$mm" ] && set_prop next_modmenu_version "$mm"
		echo "Updated gradle.properties; record the versions and the date in PRD D61."
		;;
	compile)
		gradle_next compileJava compileGametestJava
		;;
	test)
		./gradlew runNextClientGameTest
		;;
	*)
		sed -n '2,17p' "$0"
		exit 2
		;;
esac
