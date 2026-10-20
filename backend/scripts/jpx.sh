#!/bin/bash
# jpx.sh — interactive + command-line frontend for the JournalPix backend API.
#
# Interactive mode (no arguments) starts a menu:
#   scripts/jpx.sh
#
# Command mode (one-shot, scriptable):
#   scripts/jpx.sh login
#   scripts/jpx.sh register
#   scripts/jpx.sh me
#   scripts/jpx.sh trips
#   scripts/jpx.sh new-trip
#   scripts/jpx.sh trip <trip_id>
#   scripts/jpx.sh update-trip <trip_id>
#   scripts/jpx.sh del-trip <trip_id>
#   scripts/jpx.sh places <trip_id>
#   scripts/jpx.sh new-place <trip_id>
#   scripts/jpx.sh place <place_id>
#   scripts/jpx.sh update-place <place_id>
#   scripts/jpx.sh del-place <place_id>
#   scripts/jpx.sh upload <place_id> <file>
#   scripts/jpx.sh photos [place_id]
#   scripts/jpx.sh photo <photo_id>
#   scripts/jpx.sh update-photo <photo_id>
#   scripts/jpx.sh del-photo <photo_id>
#   scripts/jpx.sh download <photo_id> [dest_dir]
#   scripts/jpx.sh timeline <trip_id>
#
# Env: BASE_URL (default http://localhost:8080)
# Token is cached in $TOKEN_FILE (default /tmp/jpx_token) after login/register.
#
# JSON parsing uses only core perl (JSON::PP) — no python, no jq needed.
set -u

BASE_URL="${BASE_URL:-http://localhost:8080}"
TOKEN_FILE="${TOKEN_FILE:-/tmp/jpx_token}"
TOKEN="${JPX_TOKEN:-$(cat "$TOKEN_FILE" 2>/dev/null || true)}"

# ---------------------------------------------------------------- HTTP helpers

# request <method> <path> [curl-args...] — prints HTTP code on last line,
# body before it; sets BODY and CODE globals.
request() {
    local method="$1" path="$2"; shift 2
    local out
    out=$(curl -sS -w '\n%{http_code}' -X "$method" "$BASE_URL$path" "$@" 2>&1)
    BODY="${out%$'\n'*}"
    CODE="${out##*$'\n'}"
    [ "$CODE" -ge 200 ] 2>/dev/null && [ "$CODE" -le 299 ] 2>/dev/null
}

# auth_request — like request but adds Bearer token; fails hard if none
auth_request() {
    if [ -z "$TOKEN" ]; then
        echo "Not logged in (no token). Run: $0 login" >&2
        return 1
    fi
    request "$@" -H "Authorization: Bearer $TOKEN"
}

# ---------------------------------------------------------------- perl JSON

# jget <json> <path> — extract value, e.g. jget "$BODY" data.token
# Path segments are hash keys or array indexes: data.0.id
jget() {
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

# show_error — print server error field from BODY if present
show_error() {
    local err; err=$(jget "$BODY" error)
    [ -n "$err" ] && printf 'ERROR: %s\n' "$err" >&2
    return 1
}

# ---------------------------------------------------------------- pretty printers

pp_trips() {
    printf '%s' "$BODY" | perl -MJSON::PP -0777 -e '
        my $d = decode_json(<STDIN>);
        my @list = ref($d) eq "HASH" && ref($d->{data}) eq "ARRAY" ? @{$d->{data}}
                 : ref($d) eq "HASH" && ref($d->{data}) eq "HASH" ? ($d->{data})
                 : ref($d) eq "ARRAY" ? @$d : ($d);
        printf "%-38s %-14s %-12s %s\n", "ID", "TITLE", "STATUS", "DATES";
        for my $t (@list) {
            my $dates = ($t->{start_date}//"?")." -> ".($t->{end_date}//"?");
            printf "%-38s %-14s %-12s %s\n",
                substr($t->{id},0,36), substr($t->{title}//"",0,14),
                $t->{status}//"-", $dates;
        }
    '
}

pp_places() {
    printf '%s' "$BODY" | perl -MJSON::PP -0777 -e '
        my $d = decode_json(<STDIN>);
        my @list = ref($d) eq "HASH" && ref($d->{data}) eq "ARRAY" ? @{$d->{data}} : ref($d) eq "ARRAY" ? @$d : ();
        printf "%-38s %-18s %-6s %s\n", "ID", "NAME", "PHOTOS", "VISIT";
        for my $p (@list) {
            printf "%-38s %-18s %-6s %s\n",
                substr($p->{id},0,36), substr($p->{name}//"",0,18),
                $p->{photos_count}//0, substr($p->{visit_date}//"-",0,10);
        }
    '
}

pp_photos() {
    printf '%s' "$BODY" | perl -MJSON::PP -0777 -e '
        my $d = decode_json(<STDIN>);
        my @list = ref($d) eq "HASH" && ref($d->{data}) eq "ARRAY" ? @{$d->{data}} : ref($d) eq "HASH" && ref($d->{data}) eq "HASH" ? ($d->{data}) : ref($d) eq "ARRAY" ? @$d : ();
        printf "%-38s %-6s %-30s %s\n", "ID", "ORDER", "CAPTION", "FILE";
        for my $p (@list) {
            my $f = $p->{file_path}//""; $f =~ s{^\./}{};
            printf "%-38s %-6s %-30s %s\n",
                substr($p->{id},0,36), $p->{order_index}//0,
                substr($p->{caption}//"-",0,30), $f;
        }
    '
}

pp_user() {
    printf '%s' "$BODY" | perl -MJSON::PP -0777 -e '
        my $d = decode_json(<STDIN>);
        my $u = ref($d) eq "HASH" && ref($d->{data}) eq "HASH" ? $d->{data} : $d;
        printf "id:         %s\nemail:      %s\nname:       %s\ncreated:    %s\n",
            $u->{id}//"?", $u->{email}//"?", $u->{name}//"?", $u->{created_at}//"?";
    '
}

# Simplified timeline:
# Trip: <title> (<status>)
#   2025-06-01
#     - Eiffel Tower (2 photos)
#         * <caption or "(no caption)"> [photo_id]
pp_timeline() {
    printf '%s' "$BODY" | perl -MJSON::PP -0777 -e '
        my $d = decode_json(<STDIN>);
        $d = $d->{data} if ref($d) eq "HASH" && ref($d->{data}) eq "HASH";
        my $t = $d->{trip};
        printf "Trip: %s (%s)  %s -> %s\n", $t->{title}//"?", $t->{status}//"-",
            $t->{start_date}//"?", $t->{end_date}//"?";
        for my $item (@{$d->{timeline}//[]}) {
            printf "  %s\n", $item->{date};
            for my $p (@{$item->{places}//[]}) {
                printf "    - %s (%d photos)\n", $p->{name}//"?", scalar @{$p->{photos}//[]};
                for my $ph (@{$p->{photos}//[]}) {
                    printf "        * %s [%s]\n", $ph->{caption}//"(no caption)", substr($ph->{id},0,8);
                }
            }
        }
    '
}

# ---------------------------------------------------------------- API actions

save_token() {
    TOKEN=$(jget "$BODY" data.token)
    if [ -n "$TOKEN" ]; then
        echo "$TOKEN" > "$TOKEN_FILE" 2>/dev/null
        echo "Logged in as $(jget "$BODY" data.user.email) (token saved to $TOKEN_FILE)"
    else
        show_error
    fi
}

do_register() {
    local email name password
    [ $# -ge 3 ] && { email=$1; name=$2; password=$3; } || {
        read -rp "email: " email; read -rp "name: " name; read -rsp "password: " password; echo
    }
    request POST /api/auth/register -H 'Content-Type: application/json' \
        -d "{\"email\":\"$email\",\"name\":\"$name\",\"password\":\"$password\"}"
    [ "$CODE" = 200 ] && save_token || { echo "register failed (HTTP $CODE)"; show_error; }
}

do_login() {
    local email password
    [ $# -ge 2 ] && { email=$1; password=$2; } || {
        read -rp "email: " email; read -rsp "password: " password; echo
    }
    request POST /api/auth/login -H 'Content-Type: application/json' \
        -d "{\"email\":\"$email\",\"password\":\"$password\"}"
    [ "$CODE" = 200 ] && save_token || { echo "login failed (HTTP $CODE)"; show_error; }
}

do_me() {
    auth_request GET /api/auth/me || return 1
    [ "$CODE" = 200 ] && pp_user || { echo "failed (HTTP $CODE)"; show_error; }
}

do_update_me() {
    local name email password json='{'
    read -rp "new name (enter=skip): " name
    read -rp "new email (enter=skip): " email
    read -rsp "new password (enter=skip): " password; echo
    [ -n "$name" ]    && json+="\"name\":\"$name\","
    [ -n "$email" ]   && json+="\"email\":\"$email\","
    [ -n "$password" ] && json+="\"password\":\"$password\","
    json="${json%,}}"
    auth_request PUT /api/auth/me -H 'Content-Type: application/json' -d "$json" || return 1
    [ "$CODE" = 200 ] && { echo "Updated:"; pp_user; } || { echo "failed (HTTP $CODE)"; show_error; }
}

do_trips() {
    auth_request GET /api/trips || return 1
    [ "$CODE" = 200 ] && pp_trips || { echo "failed (HTTP $CODE)"; show_error; }
}

do_new_trip() {
    local title desc sd ed status
    read -rp "title: " title
    read -rp "description (enter=none): " desc
    read -rp "start date YYYY-MM-DD (enter=none): " sd
    read -rp "end date YYYY-MM-DD (enter=none): " ed
    read -rp "status planned|in_progress|completed|cancelled (enter=planned): " status
    local json="{\"title\":\"$title\""
    [ -n "$desc" ]   && json+=",\"description\":\"$desc\""
    [ -n "$sd" ]     && json+=",\"start_date\":\"$sd\""
    [ -n "$ed" ]     && json+=",\"end_date\":\"$ed\""
    [ -n "$status" ] && json+=",\"status\":\"$status\""
    json+="}"
    auth_request POST /api/trips -H 'Content-Type: application/json' -d "$json" || return 1
    if [ "$CODE" = 201 ]; then
        TRIP_ID=$(jget "$BODY" data.id)
        echo "Created trip $TRIP_ID"
        pp_trips
    else
        echo "failed (HTTP $CODE)"; show_error
    fi
}

do_trip() {
    request GET "/api/trips/$1" || return 1
    if [ "$CODE" = 200 ]; then
        pp_trips
        echo
        do_places "$1"
    else
        echo "failed (HTTP $CODE)"; show_error
    fi
}

do_update_trip() {
    local id="$1" title sd ed status json='{'
    read -rp "new title (enter=skip): " title
    read -rp "new start date (enter=skip): " sd
    read -rp "new end date (enter=skip): " ed
    read -rp "new status (enter=skip): " status
    [ -n "$title" ]  && json+="\"title\":\"$title\","
    [ -n "$sd" ]     && json+="\"start_date\":\"$sd\","
    [ -n "$ed" ]     && json+="\"end_date\":\"$ed\","
    [ -n "$status" ] && json+="\"status\":\"$status\","
    json="${json%,}}"
    auth_request PUT "/api/trips/$id" -H 'Content-Type: application/json' -d "$json" || return 1
    [ "$CODE" = 200 ] && { echo "Updated:"; pp_trips; } || { echo "failed (HTTP $CODE)"; show_error; }
}

do_del_trip() {
    auth_request DELETE "/api/trips/$1" || return 1
    [ "$CODE" = 200 ] && echo "Trip $1 deleted" || { echo "failed (HTTP $CODE)"; show_error; }
}

do_places() {
    request GET "/api/trips/$1/places" || return 1
    [ "$CODE" = 200 ] && pp_places || { echo "failed (HTTP $CODE)"; show_error; }
}

do_new_place() {
    local trip_id="$1" name lat lon addr visit
    local json="{\"trip_id\":\"$trip_id\""
    read -rp "place name: " name
    read -rp "latitude (enter=none): " lat
    read -rp "longitude (enter=none): " lon
    read -rp "address (enter=none): " addr
    read -rp "visit date+time YYYY-MM-DDTHH:MM:SS (enter=none): " visit
    json+=",\"name\":\"$name\""
    [ -n "$lat" ]   && json+=",\"latitude\":$lat"
    [ -n "$lon" ]   && json+=",\"longitude\":$lon"
    [ -n "$addr" ]  && json+=",\"address\":\"$addr\""
    [ -n "$visit" ] && json+=",\"visit_date\":\"$visit\""
    json+="}"
    auth_request POST "/api/trips/$trip_id/places" -H 'Content-Type: application/json' -d "$json" || return 1
    [ "$CODE" = 201 ] && { echo "Created place $(jget "$BODY" data.id)"; } || { echo "failed (HTTP $CODE)"; show_error; }
}

do_place() {
    request GET "/api/places/$1" || return 1
    if [ "$CODE" = 200 ]; then
        printf '%s' "$BODY" | perl -MJSON::PP -0777 -e '
            my $d = decode_json(<STDIN>);
            my $p = ref($d) eq "HASH" && ref($d->{data}) eq "HASH" ? $d->{data} : $d;
            printf "%s (trip %s)\n", $p->{name}//"?", substr($p->{trip_id}//"?",0,8);
            printf "  coords: %s, %s\n", $p->{latitude}//"-", $p->{longitude}//"-";
            printf "  address: %s\n", $p->{address}//"-";
            printf "  visited: %s\n", $p->{visit_date}//"-";
            printf "  photos: %d\n", scalar @{$p->{photos}//[]};
            for my $ph (@{$p->{photos}//[]}) {
                printf "    * %s [%s] %s\n", $ph->{caption}//"(no caption)",
                    substr($ph->{id},0,8), ($ph->{file_path}//"") =~ s{^\./}{}r;
            }
        '
    else
        echo "failed (HTTP $CODE)"; show_error
    fi
}

do_update_place() {
    local id="$1" name addr visit json='{'
    read -rp "new name (enter=skip): " name
    read -rp "new address (enter=skip): " addr
    read -rp "new visit date (enter=skip): " visit
    [ -n "$name" ]  && json+="\"name\":\"$name\","
    [ -n "$addr" ]  && json+="\"address\":\"$addr\","
    [ -n "$visit" ] && json+="\"visit_date\":\"$visit\","
    json="${json%,}}"
    request PUT "/api/places/$id" -H 'Content-Type: application/json' -d "$json" || return 1
    [ "$CODE" = 200 ] && echo "Place $id updated" || { echo "failed (HTTP $CODE)"; show_error; }
}

do_del_place() {
    request DELETE "/api/places/$1" || return 1
    [ "$CODE" = 200 ] && echo "Place $1 deleted" || { echo "failed (HTTP $CODE)"; show_error; }
}

do_upload() {
    local place_id="$1" file="$2"
    [ -f "$file" ] || { echo "no such file: $file" >&2; return 1; }
    auth_request POST "/api/places/$place_id/photos" -F "photo=@$file" || return 1
    if [ "$CODE" = 201 ]; then
        echo "Uploaded photo $(jget "$BODY" data.id)"
        echo "  file:  $(jget "$BODY" data.file_path)"
        echo "  thumb: $(jget "$BODY" data.thumbnail_path)"
    else
        echo "upload failed (HTTP $CODE)"; show_error
    fi
}

do_photos() {
    if [ -n "${1:-}" ]; then
        request GET "/api/places/$1/photos" || return 1
    else
        auth_request GET /api/photos || return 1
    fi
    [ "$CODE" = 200 ] && pp_photos || { echo "failed (HTTP $CODE)"; show_error; }
}

do_photo() {
    request GET "/api/photos/$1" || return 1
    [ "$CODE" = 200 ] && pp_photos || { echo "failed (HTTP $CODE)"; show_error; }
}

do_update_photo() {
    local id="$1" caption json='{'
    read -rp "caption (enter=skip): " caption
    read -rp "order index (enter=skip): " oi
    [ -n "$caption" ] && json+="\"caption\":\"$caption\","
    [ -n "$oi" ]      && json+="\"order_index\":$oi,"
    json="${json%,}}"
    request PUT "/api/photos/$id" -H 'Content-Type: application/json' -d "$json" || return 1
    [ "$CODE" = 200 ] && { echo "Updated:"; pp_photos; } || { echo "failed (HTTP $CODE)"; show_error; }
}

do_del_photo() {
    request DELETE "/api/photos/$1" || return 1
    [ "$CODE" = 200 ] && echo "Photo $1 deleted" || { echo "failed (HTTP $CODE)"; show_error; }
}

do_download() {
    local photo_id="$1" dest="${2:-.}"
    request GET "/api/photos/$photo_id" || return 1
    [ "$CODE" = 200 ] || { echo "failed (HTTP $CODE)"; show_error; return 1; }
    local path url name
    path=$(jget "$BODY" data.file_path)
    path="${path#./}"                       # ./uploads/x -> uploads/x
    url="$BASE_URL/$path"
    name="${path##*/}"
    mkdir -p "$dest"
    if curl -sS -o "$dest/$name" "$url"; then
        echo "Saved: $dest/$name"
    else
        echo "download failed" >&2; return 1
    fi
}

do_timeline() {
    request GET "/api/trips/$1/timeline" || return 1
    [ "$CODE" = 200 ] && pp_timeline || { echo "failed (HTTP $CODE)"; show_error; }
}

do_raw() {
    auth_request GET "/api$1" || return 1
    printf '%s\n' "$BODY"
}

# ---------------------------------------------------------------- interactive menu

menu() {
    while true; do
        printf '\n\033[1mJournalPix frontend\033[0m  (%s)  %s\n' "$BASE_URL" \
            "$([ -n "$TOKEN" ] && echo "logged in" || echo "NOT logged in")"
        cat <<'EOF'
  1) register          2) login           3) my account       4) update account
  5) list trips        6) new trip        7) trip detail      8) update trip
  9) delete trip      10) list places    11) new place       12) place detail
 13) update place     14) delete place   15) upload photo    16) list photos
 17) photo detail     18) update photo   19) delete photo    20) download photo
 21) timeline         22) raw API call
  q) quit
EOF
        read -rp "choice: " c
        case "$c" in
            1) do_register ;;
            2) do_login ;;
            3) do_me ;;
            4) do_update_me ;;
            5) do_trips ;;
            6) do_new_trip ;;
            7) read -rp "trip id: " id; do_trip "$id" ;;
            8) read -rp "trip id: " id; do_update_trip "$id" ;;
            9) read -rp "trip id: " id; do_del_trip "$id" ;;
            10) read -rp "trip id: " id; do_places "$id" ;;
            11) read -rp "trip id: " id; do_new_place "$id" ;;
            12) read -rp "place id: " id; do_place "$id" ;;
            13) read -rp "place id: " id; do_update_place "$id" ;;
            14) read -rp "place id: " id; do_del_place "$id" ;;
            15) read -rp "place id: " id; read -rp "file: " f; do_upload "$id" "$f" ;;
            16) read -rp "place id (enter=all mine): " id; do_photos "${id:-}" ;;
            17) read -rp "photo id: " id; do_photo "$id" ;;
            18) read -rp "photo id: " id; do_update_photo "$id" ;;
            19) read -rp "photo id: " id; do_del_photo "$id" ;;
            20) read -rp "photo id: " id; read -rp "dest dir [.]: " d; do_download "$id" "${d:-.}" ;;
            21) read -rp "trip id: " id; do_timeline "$id" ;;
            22) read -rp "path (e.g. /trips): " p; do_raw "$p" ;;
            q|Q) break ;;
            *) echo "unknown choice" ;;
        esac
    done
}

# ---------------------------------------------------------------- entry point

case "${1:-}" in
    "")            menu ;;
    register)      shift; do_register "$@" ;;
    login)         shift; do_login "$@" ;;
    me)            do_me ;;
    update-me)     do_update_me ;;
    trips)         do_trips ;;
    new-trip)      do_new_trip ;;
    trip)          do_trip "${2:?trip id required}" ;;
    update-trip)   shift; do_update_trip "$1" ;;
    del-trip)      do_del_trip "${2:?trip id required}" ;;
    places)        do_places "${2:?trip id required}" ;;
    new-place)     shift; do_new_place "$1" ;;
    place)         do_place "${2:?place id required}" ;;
    update-place)  shift; do_update_place "$1" ;;
    del-place)     do_del_place "${2:?place id required}" ;;
    upload)        do_upload "${2:?place id required}" "${3:?file required}" ;;
    photos)        do_photos "${2:-}" ;;
    photo)         do_photo "${2:?photo id required}" ;;
    update-photo)  shift; do_update_photo "$1" ;;
    del-photo)     do_del_photo "${2:?photo id required}" ;;
    download)      do_download "${2:?photo id required}" "${3:-.}" ;;
    timeline)      do_timeline "${2:?trip id required}" ;;
    raw)           shift; do_raw "$1" ;;
    help|-h|--help) sed -n '2,30p' "$0" ;;
    *) echo "unknown command: $1 (see '$0 help')" >&2; exit 1 ;;
esac
