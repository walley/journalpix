# JournalPix backend — build & install
#
#   make              build (release)
#   make install      install to PREFIX (default /usr/local) — typically as root
#   make uninstall    remove installed files
#   make clean        clean build artifacts
#
# The installed binary is meant to run as a NON-root user:
#   PREFIX/bin/journalpix-backend [--config PREFIX/etc/journalpix.conf]
# Data dirs (db file, uploads) are relative to the working directory by
# default — run it from a writable directory (e.g. a dedicated user's home).

PREFIX  ?= /usr/local
BINDIR  := $(PREFIX)/bin
ETCDIR  := $(PREFIX)/etc
CONF    := journalpix.conf
BIN     := journalpix-backend
# Source of truth for the version: [package] version in Cargo.toml
VERSION := $(shell sed -n 's/^version = "\(.*\)"/\1/p' Cargo.toml | head -n1)
HEALTH_URL ?= https://walley.org/journalpix/health

.PHONY: all build install uninstall clean version

all: build

build:
	cargo build --release

# Show the version in this tree and the version the public server reports.
# After `make install` + restart both lines must match; otherwise the old
# binary is still running.
version:
	@echo "source: $(VERSION)"
	@printf "public: "; curl -fsS -m 5 $(HEALTH_URL) | sed -n 's/.*"version":"\([^"]*\)".*/\1/p' || echo "unreachable"

install:
	install -d $(BINDIR)
	install -d $(ETCDIR)
	install -m 755 target/release/$(BIN) $(BINDIR)/$(BIN)
	# Install default config only if none exists
	if [ ! -e $(ETCDIR)/$(CONF) ]; then \
		install -m 644 journalpix.conf.example $(ETCDIR)/$(CONF); \
		echo "Installed config: $(ETCDIR)/$(CONF)"; \
	else \
		echo "Keeping existing config: $(ETCDIR)/$(CONF)"; \
	fi
	@echo
	@echo "Installed:"
	@echo "  $(BINDIR)/$(BIN)"
	@echo "  $(ETCDIR)/$(CONF)"

uninstall:
	# Remove the config too if it is still the pristine example
	if cmp -s $(ETCDIR)/$(CONF) journalpix.conf.example 2>/dev/null; then \
		rm -f $(ETCDIR)/$(CONF); \
	else \
		echo "Leaving modified config in place: $(ETCDIR)/$(CONF)"; \
	fi

clean:
	cargo clean
