#!/bin/bash
set -e

echo "Setting up Kivio development environment..."

# Install Claude Code
curl -fsSL https://claude.ai/install.sh | bash
echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.bashrc

# Ensure gradlew is executable
chmod +x /workspace/kivio-backend/gradlew

# Pre-warm Gradle dependency cache (failures are non-fatal)
echo "Warming up Gradle cache (this may take a few minutes on first run)..."
cd /workspace/kivio-backend && ./gradlew dependencies --no-daemon -q || true

echo ""
echo "Setup complete."
echo "  Backend: cd kivio-backend && ./gradlew bootRun  →  http://localhost:8080"
echo "  Mailpit:                                         →  http://localhost:8025"
echo "  claude                                           →  restart terminal first"
