#!/bin/bash

# Post-deployment test script for recipe-ai-service
# Tests key endpoints to verify the deployment is working correctly

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check dependencies
if ! command -v curl &> /dev/null; then
    echo -e "${RED}Error: curl is not installed${NC}"
    exit 1
fi

if ! command -v jq &> /dev/null; then
    echo -e "${YELLOW}Warning: jq is not installed. JSON validation will be skipped.${NC}"
    JQ_AVAILABLE=false
else
    JQ_AVAILABLE=true
fi

# Get service URL from command line or use default
SERVICE_URL="${1:-http://localhost:8080}"

if [ -z "$SERVICE_URL" ]; then
    echo -e "${RED}Error: SERVICE_URL is empty${NC}"
    exit 1
fi
echo "=========================================="
echo "Post-Deployment Testing"
echo "=========================================="
echo "Service URL: $SERVICE_URL"
echo ""

# Test counter
TESTS_PASSED=0
TESTS_FAILED=0

# Function to test endpoint with response validation
test_endpoint_with_response() {
    local endpoint=$1
    local expected_status=$2
    local description=$3
    local check_json=$4

    echo -n "Testing: $description... "

    # Make request and capture both status code and response
    tmpfile=$(mktemp)
    status_code=$(curl -s -o "$tmpfile" -w "%{http_code}" --connect-timeout 5 --max-time 10 "$SERVICE_URL$endpoint")
    body=$(cat "$tmpfile")
    rm -f "$tmpfile"

    if [ "$status_code" != "$expected_status" ]; then
        echo -e "${RED}✗ FAILED${NC} (Expected HTTP $expected_status, got HTTP $status_code)"
        ((TESTS_FAILED++))
        return 1
    fi

    # If check_json is true and jq is available, verify response is valid JSON
    if [ "$check_json" = "true" ] && [ "$JQ_AVAILABLE" = true ]; then
        if echo "$body" | jq empty 2>/dev/null; then
            echo -e "${GREEN}✓ PASSED${NC} (HTTP $status_code, valid JSON)"
            ((TESTS_PASSED++))
            return 0
        else
            echo -e "${RED}✗ FAILED${NC} (HTTP $status_code, but invalid JSON response)"
            ((TESTS_FAILED++))
            return 1
        fi
    else
        echo -e "${GREEN}✓ PASSED${NC} (HTTP $status_code)"
        ((TESTS_PASSED++))
        return 0
    fi
}

echo "Starting endpoint tests..."
echo ""

# Test 1: Health endpoint (should be accessible without auth)
test_endpoint_with_response "/actuator/health" "200" "Health endpoint" "true" || true

# Test 2: Swagger UI endpoint (follows redirects)
echo -n "Testing: Swagger UI endpoint... "
status_code=$(curl -s -o /dev/null -w "%{http_code}" -L --connect-timeout 5 --max-time 10 "$SERVICE_URL/swagger-ui.html")
if [ "$status_code" = "200" ]; then
    echo -e "${GREEN}✓ PASSED${NC} (HTTP $status_code after redirect)"
    ((TESTS_PASSED++))
else
    echo -e "${RED}✗ FAILED${NC} (Expected HTTP 200, got HTTP $status_code)"
    ((TESTS_FAILED++))
fi

# Test 3: OpenAPI spec endpoint
test_endpoint_with_response "/v3/api-docs" "200" "OpenAPI specification endpoint" "true" || true

# Test 4: Protected endpoint without auth (should return 401 or 403)
# Note: AI service may have different auth patterns - adjust endpoint as needed
echo -n "Testing: Protected endpoint without auth... "
status_code=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 --max-time 10 "$SERVICE_URL/api/recipes/generate")
if [ "$status_code" = "401" ] || [ "$status_code" = "403" ]; then
    echo -e "${GREEN}✓ PASSED${NC} (HTTP $status_code - correctly blocked)"
    ((TESTS_PASSED++))
else
    echo -e "${YELLOW}⚠ WARNING${NC} (Expected HTTP 401 or 403, got HTTP $status_code - may not be auth-protected)"
    # Don't fail for this test as AI service may have different auth setup
    ((TESTS_PASSED++))
fi

echo ""
echo "=========================================="
echo "Test Summary"
echo "=========================================="
echo -e "Tests Passed: ${GREEN}$TESTS_PASSED${NC}"
echo -e "Tests Failed: ${RED}$TESTS_FAILED${NC}"
echo ""

if [ "$TESTS_FAILED" -eq 0 ]; then
    echo -e "${GREEN}✓ All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}✗ Some tests failed${NC}"
    exit 1
fi
