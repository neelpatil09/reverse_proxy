# test server for reverse proxy to target
from http.server import BaseHTTPRequestHandler, HTTPServer

class HelloHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        print("---- HEADERS FROM PROXY ----")
        for k, v in self.headers.items():
            print(f"{k}: {v}")
        print("----------------------------")

        self.send_response(200)
        self.send_header("Content-type", "text/plain")
        self.end_headers()
        self.wfile.write(b"Hello World\n")

    def do_POST(self):
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"Hello POST\n")

if __name__ == "__main__":
    server = HTTPServer(("0.0.0.0", 8081), HelloHandler)
    print("Serving on port 8081...")
    server.serve_forever()
