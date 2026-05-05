#include <rtc/rtc.hpp>
#include <nlohmann/json.hpp>
#include <iostream>
#include <memory>
#include <string>

using nlohmann::json;

namespace {

void writeLine(const json& frame) {
    std::cout << frame.dump() << '\n';
    std::cout.flush();
}

void logLine(const std::string& level, const std::string& message) {
    writeLine(json{
        {"type", "log"},
        {"payload", {{"level", level}, {"message", message}}},
    });
}

std::string asString(const json& node, const std::string& key) {
    if (!node.contains(key) || !node[key].is_string()) {
        return "";
    }
    return node[key].get<std::string>();
}

int asIntOrDefault(const json& node, const std::string& key, int def) {
    if (!node.contains(key) || !node[key].is_number_integer()) {
        return def;
    }
    return node[key].get<int>();
}

rtc::Description::Type parseType(const std::string& type) {
    if (type == "answer") {
        return rtc::Description::Type::Answer;
    }
    return rtc::Description::Type::Offer;
}

}  // namespace

int main() {
    std::ios::sync_with_stdio(false);
    std::cin.tie(nullptr);

    rtc::InitLogger(rtc::LogLevel::Warning);

    rtc::Configuration cfg;
    cfg.iceServers.emplace_back("stun:stun.l.google.com:19302");

    std::shared_ptr<rtc::PeerConnection> pc;
    std::string lastRemoteSdp;

    writeLine(json{
        {"type", "helper-ready"},
        {"payload", {{"mode", "native-helper"}, {"webrtcBridge", true}, {"engine", "libdatachannel"}}},
    });

    std::string line;
    while (std::getline(std::cin, line)) {
        if (line.empty()) {
            continue;
        }

        json frame;
        try {
            frame = json::parse(line);
        } catch (const std::exception& e) {
            logLine("error", std::string("invalid json: ") + e.what());
            continue;
        }

        const std::string type = asString(frame, "type");
        const json payload = frame.value("payload", json::object());

        if (type == "shutdown") {
            writeLine(json{{"type", "shutdown-ack"}});
            break;
        }

        if (type == "offer") {
            const std::string remoteSdp = asString(payload, "sdp");
            const std::string remoteType = asString(payload, "sdpType").empty() ? "offer" : asString(payload, "sdpType");
            if (remoteSdp.empty()) {
                logLine("error", "offer without sdp");
                continue;
            }

            try {
                if (pc) {
                    logLine("info", "replacing existing peer connection for new offer");
                    pc.reset();
                }
                pc = std::make_shared<rtc::PeerConnection>(cfg);
                lastRemoteSdp = remoteSdp;

                pc->onLocalDescription([](rtc::Description description) {
                    writeLine(json{
                        {"type", "answer"},
                        {"payload",
                         {{"mode", "webrtc"},
                          {"sdp", std::string(description)},
                          {"sdpType", description.typeString()}}},
                    });
                });

                pc->onLocalCandidate([](rtc::Candidate candidate) {
                    int mline = 0;
                    try {
                        std::string mid = candidate.mid();
                        if (!mid.empty()) {
                            mline = std::stoi(mid);
                        }
                    } catch (...) {
                        mline = 0;
                    }
                    writeLine(json{
                        {"type", "ice-candidate"},
                        {"payload",
                         {{"mode", "webrtc"},
                          {"candidate", std::string(candidate)},
                          {"sdpMid", candidate.mid()},
                          {"sdpMLineIndex", mline}}},
                    });
                });

                pc->onStateChange([](rtc::PeerConnection::State state) {
                    logLine("info", std::string("pc state: ") + rtc::stateToString(state));
                });

                pc->setRemoteDescription(rtc::Description(remoteSdp, parseType(remoteType)));
                logLine("info", "remote offer applied");
            } catch (const std::exception& e) {
                logLine("error", std::string("offer handling failed: ") + e.what());
            }
            continue;
        }

        if (type == "ice-candidate") {
            if (!pc) {
                logLine("warn", "candidate ignored: peer connection is not initialized");
                continue;
            }
            try {
                const std::string candidate = asString(payload, "candidate");
                const std::string sdpMid = asString(payload, "sdpMid");
                const int sdpMLineIndex = asIntOrDefault(payload, "sdpMLineIndex", 0);
                if (candidate.empty()) {
                    logLine("warn", "candidate payload without candidate field");
                    continue;
                }
                std::string candidateMid = sdpMid.empty() ? std::to_string(sdpMLineIndex) : sdpMid;
                pc->addRemoteCandidate(rtc::Candidate(candidate, candidateMid));
                logLine("info", "remote candidate accepted");
            } catch (const std::exception& e) {
                logLine("error", std::string("candidate handling failed: ") + e.what());
            }
            continue;
        }

        logLine("debug", std::string("unhandled frame type: ") + type);
    }

    pc.reset();
    return 0;
}
