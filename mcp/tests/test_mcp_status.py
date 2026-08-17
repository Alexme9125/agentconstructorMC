from printcore_mcp.client import PrintCoreClient
from printcore_mcp.server import handle


def test_status_when_unreachable():
    client = PrintCoreClient(base="http://127.0.0.1:1", token="x", timeout=0.2)
    message = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "tools/call",
        "params": {"name": "printcore_status", "arguments": {}},
    }
    response = handle(message, client)
    assert response["result"]["content"][0]["text"]
    assert "reachable" in response["result"]["content"][0]["text"]
