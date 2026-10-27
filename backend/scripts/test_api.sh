#!/bin/bash
# JournalPix backend smoke-test "frontend" — exercises the whole API with curl.
#
# Usage:
#   scripts/test_api.sh                 # run everything
#   scripts/test_api.sh <section>       # health | auth | trips | places | photos | timeline | crud
#   BASE_URL=http://host:18888 scripts/test_api.sh
#
# Requires: curl, perl (core JSON::PP — no python/jq needed)
set -u

BASE_URL="${BASE_URL:-http://localhost:18888}"
SECTION="${1:-all}"

PASS=0; FAIL=0

say()  { printf '\n\033[1m== %s ==\033[0m\n' "$*"; }
ok()   { PASS=$((PASS+1)); printf '  \033[32mPASS\033[0m %s\n' "$*"; }
bad()  { FAIL=$((FAIL+1)); printf '  \033[31mFAIL\033[0m %s\n' "$*"; }

# check <name> <expected_status> <actual_status> <body>
check() {
    local name="$1" want="$2" got="$3" body="${4:-}"
    if [ "$want" = "$got" ]; then ok "$name (HTTP $got)"; else bad "$name (want $want, got $got) ${body:0:200}"; fi
}

# json_get <body> <dotted-path>  e.g. json_get "$BODY" data.token
json_get() {
    printf '%s' "$1" | perl -MJSON::PP -0777 -e '
        my $path = shift @ARGV;
        my $d;
        eval { $d = decode_json(<STDIN>); 1 } or exit 1;
        for my $k (split /\./, defined $path ? $path : "") {
            if (ref($d) eq "ARRAY") { $d = $d->[$k+0]; }
            elsif (ref($d) eq "HASH") { $d = $d->{$k}; }
            else { $d = undef; }
            exit 1 unless defined $d;
        }
        if (!defined $d) { exit 1 }
        elsif (ref($d) eq "ARRAY") { print join("\n", map { defined($_) ? $_ : "" } @$d), "\n" }
        elsif (ref($d) eq "HASH")  { print encode_json($d) }
        else { print $d }
    ' "$2" 2>/dev/null
}

post() { curl -s -w '\n%{http_code}' -X POST "$@"; }
get()  { curl -s -w '\n%{http_code}' "$@"; }
put()  { curl -s -w '\n%{http_code}' -X PUT "$@"; }
del()  { curl -s -w '\n%{http_code}' -X DELETE "$@"; }

split() { local out="$1"; BODY="${out%$'\n'*}"; CODE="${out##*$'\n'}"; }

RANDOM_ID="$$$(date +%s)"
EMAIL="test_$RANDOM_ID@example.com"

run_health() {
    say "health"
    split "$(get "$BASE_URL/")"
    check "GET / (about page)" 200 "$CODE" "$BODY"
    split "$(get "$BASE_URL/health")"
    check "GET /health" 200 "$CODE" "$BODY"
}

run_auth() {
    say "auth (register/login/me)"
    split "$(post "$BASE_URL/api/auth/register" -H 'Content-Type: application/json' \
        -d "{\"email\":\"$EMAIL\",\"name\":\"Test User\",\"password\":\"password123\"}")"
    check "POST /api/auth/register" 200 "$CODE" "$BODY"
    TOKEN="$(json_get "$BODY" data.token)"

    split "$(post "$BASE_URL/api/auth/login" -H 'Content-Type: application/json' \
        -d "{\"email\":\"$EMAIL\",\"password\":\"password123\"}")"
    check "POST /api/auth/login" 200 "$CODE" "$BODY"
    [ -z "$TOKEN" ] && TOKEN="$(json_get "$BODY" data.token)"

    split "$(post "$BASE_URL/api/auth/login" -H 'Content-Type: application/json' \
        -d "{\"email\":\"$EMAIL\",\"password\":\"wrongpass1\"}")"
    check "login with wrong password -> 401" 401 "$CODE" "$BODY"

    split "$(get "$BASE_URL/api/auth/me" -H "Authorization: Bearer $TOKEN")"
    check "GET /api/auth/me" 200 "$CODE" "$BODY"

    split "$(get "$BASE_URL/api/auth/me")"
    check "GET /api/auth/me without token -> 401" 401 "$CODE" "$BODY"

    split "$(put "$BASE_URL/api/auth/me" -H "Authorization: Bearer $TOKEN" \
        -H 'Content-Type: application/json' -d '{"name":"Renamed User"}')"
    check "PUT /api/auth/me (rename)" 200 "$CODE" "$BODY"
}

run_trips() {
    say "trips CRUD"
    [ -z "${TOKEN:-}" ] && { bad "no token (run auth first)"; return; }
    split "$(post "$BASE_URL/api/trips" -H "Authorization: Bearer $TOKEN" \
        -H 'Content-Type: application/json' \
        -d '{"title":"Test Trip","description":"from test script","start_date":"2025-06-01","end_date":"2025-06-10","status":"planned"}')"
    check "POST /api/trips" 201 "$CODE" "$BODY"
    TRIP_ID="$(json_get "$BODY" data.id)"

    split "$(get "$BASE_URL/api/trips" -H "Authorization: Bearer $TOKEN")"
    check "GET /api/trips" 200 "$CODE" "$BODY"

    split "$(get "$BASE_URL/api/trips/$TRIP_ID" -H "Authorization: Bearer $TOKEN")"
    check "GET /api/trips/{id}" 200 "$CODE" "$BODY"

    split "$(put "$BASE_URL/api/trips/$TRIP_ID" -H "Authorization: Bearer $TOKEN" \
        -H 'Content-Type: application/json' -d '{"status":"in_progress"}')"
    check "PUT /api/trips/{id} (status)" 200 "$CODE" "$BODY"

    split "$(get "$BASE_URL/api/trips")"
    check "unauthorized trips list -> 401" 401 "$CODE" "$BODY"
}

run_places() {
    say "places CRUD"
    [ -z "${TRIP_ID:-}" ] && { bad "no trip (run trips first)"; return; }
    split "$(post "$BASE_URL/api/trips/$TRIP_ID/places" -H "Authorization: Bearer $TOKEN" \
        -H 'Content-Type: application/json' \
        -d "{\"trip_id\":\"$TRIP_ID\",\"name\":\"Eiffel Tower\",\"latitude\":48.8584,\"longitude\":2.2945,\"visit_date\":\"2025-06-01T14:30:00\"}")"
    check "POST /api/trips/{id}/places" 201 "$CODE" "$BODY"
    PLACE_ID="$(json_get "$BODY" data.id)"

    split "$(get "$BASE_URL/api/trips/$TRIP_ID/places")"
    check "GET /api/trips/{id}/places" 200 "$CODE" "$BODY"

    split "$(get "$BASE_URL/api/places/$PLACE_ID")"
    check "GET /api/places/{id}" 200 "$CODE" "$BODY"

    split "$(put "$BASE_URL/api/places/$PLACE_ID" -H 'Content-Type: application/json' \
        -d '{"address":"Champ de Mars, Paris"}')"
    check "PUT /api/places/{id}" 200 "$CODE" "$BODY"

    split "$(post "$BASE_URL/api/trips/00000000-0000-0000-0000-000000000000/places" \
        -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
        -d "{\"trip_id\":\"00000000-0000-0000-0000-000000000000\",\"name\":\"Nowhere\"}")"
    check "create place in missing trip -> 404" 404 "$CODE" "$BODY"
}

run_photos() {
    say "photos (multipart upload)"
    [ -z "${PLACE_ID:-}" ] && { bad "no place (run places first)"; return; }
    PNG="$(mktemp /tmp/jpx_XXXX.png)"
    printf '\x89PNG\r\n\x1a\n\x00\x00\x00\x0dIHDR' > "$PNG"

    split "$(post "$BASE_URL/api/places/$PLACE_ID/photos" -H "Authorization: Bearer $TOKEN" \
        -F "photo=@$PNG;type=image/png")"
    check "POST /api/places/{id}/photos (upload)" 201 "$CODE" "$BODY"
    PHOTO_ID="$(json_get "$BODY" data.id)"
    FILE_PATH="$(json_get "$BODY" data.file_path)"
    FILE_NAME="${FILE_PATH##*/}"

    split "$(get "$BASE_URL/api/places/$PLACE_ID/photos")"
    check "GET /api/places/{id}/photos" 200 "$CODE" "$BODY"

    split "$(get "$BASE_URL/api/photos" -H "Authorization: Bearer $TOKEN")"
    check "GET /api/photos (all mine)" 200 "$CODE" "$BODY"

    split "$(get "$BASE_URL/api/photos/$PHOTO_ID")"
    check "GET /api/photos/{id}" 200 "$CODE" "$BODY"

    split "$(put "$BASE_URL/api/photos/$PHOTO_ID" -H 'Content-Type: application/json' \
        -d '{"caption":"Test caption"}')"
    check "PUT /api/photos/{id} (caption)" 200 "$CODE" "$BODY"

    split "$(get "$BASE_URL/uploads/$FILE_NAME")"
    check "GET /uploads/<file> (static)" 200 "$CODE" "$BODY"

    rm -f "$PNG"
}

run_timeline() {
    say "timeline"
    [ -z "${TRIP_ID:-}" ] && { bad "no trip (run trips first)"; return; }
    split "$(get "$BASE_URL/api/trips/$TRIP_ID/timeline")"
    check "GET /api/trips/{id}/timeline" 200 "$CODE" "$BODY"

    if [ -n "${PLACE_ID:-}" ]; then
        split "$(put "$BASE_URL/api/trips/$TRIP_ID/timeline/reorder" \
            -H 'Content-Type: application/json' -d "[\"$PLACE_ID\"]")"
        check "PUT timeline reorder" 200 "$CODE" "$BODY"
    fi
}

run_crud() {
    say "cleanup (delete cascade)"
    if [ -n "${PHOTO_ID:-}" ]; then
        split "$(del "$BASE_URL/api/photos/$PHOTO_ID")"
        check "DELETE /api/photos/{id}" 200 "$CODE" "$BODY"
    fi
    if [ -n "${PLACE_ID:-}" ]; then
        split "$(del "$BASE_URL/api/places/$PLACE_ID")"
        check "DELETE /api/places/{id}" 200 "$CODE" "$BODY"
    fi
    if [ -n "${TRIP_ID:-}" ]; then
        split "$(del "$BASE_URL/api/trips/$TRIP_ID" -H "Authorization: Bearer $TOKEN")"
        check "DELETE /api/trips/{id}" 200 "$CODE" "$BODY"
    fi
    split "$(del "$BASE_URL/api/trips/00000000-0000-0000-0000-000000000000")"
    check "delete missing trip -> 404" 404 "$CODE" "$BODY"
}

case "$SECTION" in
    all)       run_health; run_auth; run_trips; run_places; run_photos; run_timeline; run_crud ;;
    health)    run_health ;;
    auth)      run_auth ;;
    trips)     run_auth >/dev/null 2>&1; run_trips ;;
    places)    run_auth >/dev/null 2>&1; run_trips >/dev/null 2>&1; run_places ;;
    photos)    run_auth >/dev/null 2>&1; run_trips >/dev/null 2>&1; run_places >/dev/null 2>&1; run_photos ;;
    timeline)  run_auth >/dev/null 2>&1; run_trips >/dev/null 2>&1; run_places >/dev/null 2>&1; run_timeline ;;
    crud)      run_crud ;;
    *) echo "unknown section: $SECTION (use: all|health|auth|trips|places|photos|timeline|crud)"; exit 1 ;;
esac

printf '\n\033[1mResult: %d passed, %d failed\033[0m\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ]
