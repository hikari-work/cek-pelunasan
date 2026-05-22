package miniapp

import (
	"bytes"
	"io"
	"log/slog"
	"time"

	"github.com/gofiber/fiber/v2"
)

// loggerMiddleware mencatat setiap HTTP request ke miniapp API dengan request dan response body.
func loggerMiddleware() fiber.Handler {
	return func(c *fiber.Ctx) error {
		start := time.Now()
		path := c.Path()
		method := c.Method()

		// Capture request body
		var reqBody string
		if len(c.Body()) > 0 {
			reqBody = string(c.Body())
		}

		// Process request
		err := c.Next()

		// Capture response body
		var respBody string
		if c.Response() != nil && len(c.Response().Body()) > 0 {
			respBody = string(c.Response().Body())
			// Limit response body to 5000 chars to avoid huge logs
			if len(respBody) > 5000 {
				respBody = respBody[:5000] + "... (truncated)"
			}
		}

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

		// Add request body if present
		if reqBody != "" {
			attrs = append(attrs, "request_body", reqBody)
		}

		// Add response body if present
		if respBody != "" {
			attrs = append(attrs, "response_body", respBody)
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

// captureResponseBody wraps response writer to capture body
func captureResponseBody(c *fiber.Ctx) ([]byte, error) {
	// Read response body
	body := c.Response().Body()
	if len(body) == 0 {
		return nil, nil
	}

	// Copy body
	buf := bytes.NewBuffer(body)
	return io.ReadAll(buf)
}
