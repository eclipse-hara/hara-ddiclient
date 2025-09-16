#!/bin/bash

# A script to upload a Bill of Materials (BOM) file via a POST request.

# --- Configuration ---
# Check if exactly 4 arguments are provided. If not, print usage info and exit.
if [ "$#" -ne 5 ]; then
    echo "❌ Incorrect number of arguments."
    echo "Usage: $0 <host> <api-key> <app-version> <project-name> <bom-file-path>"
    echo "Example: $0 'http://localhost:8081' 'yourSecretApiKey' '1.2.3' 'UF-Service' './bom.json'"
    exit 1
fi

# Assign command-line arguments to variables for clarity.
HOST="$1"
API_KEY="$2"
PROJECT_VERSION="$3"
BOM_FILE="$5"
NAME="$4"

# --- Pre-flight Check ---
# Verify that the specified BOM file exists and is readable.

#if [ ! -f "$BOM_FILE" ]; then
#    echo "❌ Error: The file '$BOM_FILE' does not exist."
#    exit 1
#fi

# --- Execution ---
echo "🚀 Uploading BOM file '$BOM_FILE'..."
echo "   Host: $HOST"
echo "   Project: $NAME"
echo "   Version: $PROJECT_VERSION"

# Execute the curl command with the provided variables.
# The backslashes (\) are used to split the long command across multiple lines for readability.
#curl -X "POST" "${HOST}/api/v1/bom" \
#     -H "Content-Type: multipart/form-data" \
#     -H "X-Api-Key: ${API_KEY}" \
#     -F "autoCreate=true" \
#     -F "projectName=UF-service" \
#     -F "projectVersion=${PROJECT_VERSION}" \
#     -F "bom=${BOM_FILE}"

curl -s \
     -w "\n---\nHTTP Status Code: %{http_code}\n" \
     -X "POST" "${HOST}/api/v1/bom" \
     -H "Content-Type: multipart/form-data" \
     -H "X-Api-Key: ${API_KEY}" \
     -F "autoCreate=true" \
     -F "projectName=$NAME" \
     -F "projectVersion=${PROJECT_VERSION}" \
     -F "bom=@${BOM_FILE}"

# Check the exit code of the curl command.
#if [ $? -eq 0 ]; then
#    echo -e "\n✅ BOM uploaded successfully."
#else
#    echo -e "\n⚠️ An error occurred during the upload."
#fi
