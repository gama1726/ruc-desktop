package ru.ruc.desktop.engine;

/**
 * Entry point for the host-side agent process.
 *
 * <p>Delegates to {@link AgentRuntime} which owns control-plane, signaling and media modules.
 */
public class AgentSignalingClient {

    public static void main(String[] args) throws Exception {
        AgentConfig cfg = AgentConfig.fromEnv();
        new AgentRuntime(cfg).runForever();
    }
}
