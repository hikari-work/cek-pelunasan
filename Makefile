.PHONY: build run run-env test lint tidy clean

BIN := bin/cekpelunasan
PKG := ./cmd/cekpelunasan

build:
	@mkdir -p bin
	go build -o $(BIN) $(PKG)

# run: jalan langsung. Binary auto-load .env kalau ada di working directory.
# Override path via ENV_FILE=path/to/.env make run.
run:
	go run $(PKG)

# run-env: opsi alternatif — export semua var dari .env sebagai shell env
# sebelum go run. Berguna kalau perlu env tersebut juga ada di sub-process
# (mis. wkhtmltopdf yang spawn child).
run-env:
	@test -f .env || (echo "❌ .env tidak ditemukan"; exit 1)
	export $$(grep -v '^#' .env | xargs) && go run $(PKG)

test:
	go test ./...

lint:
	golangci-lint run

tidy:
	go mod tidy

clean:
	rm -rf bin
