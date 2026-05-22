package miniapp

import (
	"log/slog"
	"time"

	"github.com/gofiber/fiber/v2"
)

// loggerMiddleware mencatat setiap HTTP request ke miniapp API.
func loggerMiddleware() fiber.Handler {
	return func(c *fiber.Ctx) error {
		start := time.Now()
		path := c.Path()
		method := c.Method()

		// Process request
		err := c.Next()

		// Log after request completed
		duration := time.Since(start)
		status := c.Response().StatusCode()

		attrs := []any{
			"method", method,
			"path", path,
			"status", status,
			"duration_ms", duration.Milliseconds(),
			"ip", c.IP(),
		}

		// Add user_id if available from session
		if userID := c.Locals("user_id"); userID != nil {
			attrs = append(attrs, "user_id", userID)
		}

		// Add chat_id if available from session
		if chatID := c.Locals("chat_id"); chatID != nil {
			attrs = append(attrs, "chat_id", chatID)
		}

		// Log level based on status code
		if status >= 500 {
			slog.Error("miniapp request", attrs...)
		} else if status >= 400 {
			slog.Warn("miniapp request", attrs...)
		} else {
			slog.Info("miniapp request", attrs...)
		}

		return err
	}
}
