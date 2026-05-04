#include <iostream>
#include <string>

namespace {

std::string jsonEscape(const std::string& input) {
    std::string out;
    out.reserve(input.size() + 16);
    for (char c : input) {
        switch (c) {
            case '\\':
                out += "\\\\";
                break;
            case '"':
                out += "\\\"";
                break;
            case '\n':
                out += "\\n";
                break;
            case '\r':
                out += "\\r";
                break;
            case '\t':
                out += "\\t";
                break;
            default:
                out += c;
                break;
        }
    }
    return out;
}

bool contains(const std::string& s, const std::string& needle) {
    return s.find(needle) != std::string::npos;
}

}  // namespace

int main() {
    std::ios::sync_with_stdio(false);
    std::cin.tie(nullptr);

    std::cout << R"({"type":"helper-ready","payload":{"mode":"stub-cpp","webrtcBridge":false}})" << '\n';
    std::cout.flush();

    std::string line;
    while (std::getline(std::cin, line)) {
        if (line.empty()) {
            continue;
        }

        if (contains(line, "\"type\":\"shutdown\"")) {
            std::cout << R"({"type":"shutdown-ack"})" << '\n';
            std::cout.flush();
            break;
        }

        if (contains(line, "\"type\":\"offer\"")) {
            std::cout
                << R"({"type":"answer","payload":{"mode":"native-helper","sdp":"","sdpType":"answer","note":"C++ helper stub: integrate libwebrtc bridge"}})"
                << '\n';
            std::cout
                << R"({"type":"ice-candidate","payload":{"mode":"native-helper","candidate":"stub-candidate","sdpMid":"0","sdpMLineIndex":0}})"
                << '\n';
            std::cout.flush();
            continue;
        }

        if (contains(line, "\"type\":\"ice-candidate\"")) {
            std::cout << R"({"type":"log","payload":{"level":"info","message":"remote candidate accepted by helper stub"}})" << '\n';
            std::cout.flush();
            continue;
        }

        std::cout << "{\"type\":\"log\",\"payload\":{\"level\":\"debug\",\"message\":\"unhandled: "
                  << jsonEscape(line) << "\"}}\n";
        std::cout.flush();
    }

    return 0;
}
