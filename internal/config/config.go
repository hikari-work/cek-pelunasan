package config

import (
	"fmt"
	"os"
	"strconv"
	"time"
)

type Config struct {
	Mongo     MongoConfig
	Telegram  TelegramConfig
	R2        R2Config
	WhatsApp  WhatsAppConfig
	SLIK      SLIKConfig
	Mail      MailConfig
	Server    ServerConfig
}

type MongoConfig struct {
	URI string
}

type TelegramConfig struct {
	BotToken string
	OwnerID  int64
}

type R2Config struct {
	AccessKey string
	AccountID string
	SecretKey string
	Endpoint  string
	Bucket    string
}

type WhatsAppConfig struct {
	GatewayURL      string
	GatewayUsername string
	GatewayPassword string
	AdminNumber     string
	ForwardEmailTo  string
	ForwardEmailFrom string
}

type SLIKConfig struct {
	PDFEndpointURL  string
	PDFLogoURL      string
	MaxPDFSize      int64
	SearchTimeout   time.Duration
	MaxResults      int
}

type MailConfig struct {
	Host     string
	Port     int
	Username string
	Password string
	UseSSL   bool
}

type ServerConfig struct {
	Port int
}

func Load() (*Config, error) {
	ownerID, err := getEnvInt64("TELEGRAM_BOT_OWNER", 0)
	if err != nil {
		return nil, fmt.Errorf("TELEGRAM_BOT_OWNER: %w", err)
	}

	mailPort, err := getEnvInt("MAIL_PORT", 465)
	if err != nil {
		return nil, fmt.Errorf("MAIL_PORT: %w", err)
	}

	serverPort, err := getEnvInt("SERVER_PORT", 8080)
	if err != nil {
		return nil, fmt.Errorf("SERVER_PORT: %w", err)
	}

	maxPDFSize, err := getEnvInt64("SLIK_PDF_MAX_SIZE", 5242880000)
	if err != nil {
		return nil, fmt.Errorf("SLIK_PDF_MAX_SIZE: %w", err)
	}

	searchTimeoutSec, err := getEnvInt("SLIK_SEARCH_TIMEOUT_SECONDS", 30)
	if err != nil {
		return nil, fmt.Errorf("SLIK_SEARCH_TIMEOUT_SECONDS: %w", err)
	}

	maxResults, err := getEnvInt("SLIK_MAX_RESULTS", 50)
	if err != nil {
		return nil, fmt.Errorf("SLIK_MAX_RESULTS: %w", err)
	}

	return &Config{
		Mongo: MongoConfig{
			URI: getEnv("SPRING_DATA_MONGODB_URI", "mongodb://localhost:27017/cek_pelunasan"),
		},
		Telegram: TelegramConfig{
			BotToken: os.Getenv("TELEGRAM_BOT_TOKEN"),
			OwnerID:  ownerID,
		},
		R2: R2Config{
			AccessKey: os.Getenv("R2_ACCESS_KEY"),
			AccountID: os.Getenv("R2_ACCOUNT_ID"),
			SecretKey: os.Getenv("R2_SECRET_KEY"),
			Endpoint:  os.Getenv("R2_ENDPOINT"),
			Bucket:    os.Getenv("R2_BUCKET"),
		},
		WhatsApp: WhatsAppConfig{
			GatewayURL:       getEnv("WHATSAPP_GATEWAY_URL", "http://localhost:3000"),
			GatewayUsername:  getEnv("WHATSAPP_GATEWAY_USERNAME", "admin"),
			GatewayPassword:  getEnv("WHATSAPP_GATEWAY_PASSWORD", "admin"),
			AdminNumber:      os.Getenv("ADMIN_WHATSAPP"),
			ForwardEmailTo:   os.Getenv("EMAIL_FORWARD_RECIPIENT"),
			ForwardEmailFrom: os.Getenv("EMAIL_FORWARD_FROM"),
		},
		SLIK: SLIKConfig{
			PDFEndpointURL: getEnv("PDF_ENDPOINT_URL", "https://kredit.suryayudha.id/ideb/generate.php"),
			PDFLogoURL:     getEnv("PDF_LOGO_URL", "https://kredit.suryayudha.id/ideb/logo.png"),
			MaxPDFSize:     maxPDFSize,
			SearchTimeout:  time.Duration(searchTimeoutSec) * time.Second,
			MaxResults:     maxResults,
		},
		Mail: MailConfig{
			Host:     getEnv("MAIL_HOST", "smtp.sumopod.com"),
			Port:     mailPort,
			Username: os.Getenv("MAIL_USERNAME"),
			Password: os.Getenv("MAIL_PASSWORD"),
			UseSSL:   true,
		},
		Server: ServerConfig{
			Port: serverPort,
		},
	}, nil
}

func (c *Config) Validate() error {
	if c.Telegram.BotToken == "" {
		return fmt.Errorf("TELEGRAM_BOT_TOKEN required")
	}
	if c.Mongo.URI == "" {
		return fmt.Errorf("SPRING_DATA_MONGODB_URI required")
	}
	return nil
}

func getEnv(key, fallback string) string {
	if v, ok := os.LookupEnv(key); ok && v != "" {
		return v
	}
	return fallback
}

func getEnvInt(key string, fallback int) (int, error) {
	v, ok := os.LookupEnv(key)
	if !ok || v == "" {
		return fallback, nil
	}
	n, err := strconv.Atoi(v)
	if err != nil {
		return 0, err
	}
	return n, nil
}

func getEnvInt64(key string, fallback int64) (int64, error) {
	v, ok := os.LookupEnv(key)
	if !ok || v == "" {
		return fallback, nil
	}
	n, err := strconv.ParseInt(v, 10, 64)
	if err != nil {
		return 0, err
	}
	return n, nil
}
