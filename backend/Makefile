# JournalPix backend — build & install
#
#   make              build (debug)
#   make release      build (optimized)
#   make install      install to PREFIX (default /usr/local) — typically as root
#   make uninstall    remove installed files
#   make test         run the curl smoke test against localhost:8080
#   make run          run dev server from the repo
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

.PHONY: all build release install uninstall test run clean

all: build

build:
	cargo build

release:
	cargo build --release

# Root is normally required to write to PREFIX; that's the caller's problem.
install: release
	install -d $(BINDIR)
	install -d $(ETCDIR)
	install -m 755 target/release/$(BIN) $(BINDIR)/$(BIN)
	# Install default config only if none exists yet (never clobber)
	if [ ! -e $(ETCDIR)/$(CONF) ]; then \
		install -m 644 journalpix.conf.example $(ETCDIR)/$(CONF); \
		echo "Installed config: $(ETCDIR)/$(CONF)"; \
	else \
		echo "Keeping existing config: $(ETCDIR)/$(CONF)"; \
	fi
	# Keep the example next to it for reference
	install -m 644 journalpix.conf.example $(ETCDIR)/$(CONF).example
	@echo
	@echo "Installed:"
	@echo "  $(BINDIR)/$(BIN)"
	@echo "  $(ETCDIR)/$(CONF)  (edit port/jwt_secret/storage paths there)"
	@echo "Run as a non-root user, from a writable working directory."

uninstall:
	rm -f $(BINDIR)/$(BIN) $(ETCDIR)/$(CONF).example
	# Remove the config too if it is still the pristine example
	if cmp -s $(ETCDIR)/$(CONF) journalpix.conf.example 2>/dev/null; then \
		rm -f $(ETCDIR)/$(CONF); \
	else \
		echo "Leaving modified config in place: $(ETCDIR)/$(CONF)"; \
	fi

test:
	./scripts/test_api.sh

run:
	cargo run

clean:
	cargo clean
