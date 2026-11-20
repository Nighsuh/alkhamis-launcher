package com.gathin.tvhome;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;

/**
 * Classic ADB over TCP to this TV only (network debugging, usually port 5555).
 * No TLS pairing stack: that pulled in Conscrypt and Bouncy Castle for a screen almost nobody opens.
 */
final class AdbLink {
    private static final int A_CNXN = 0x4e584e43;
    private static final int A_AUTH = 0x48545541;
    private static final int A_OPEN = 0x4e45504f;
    private static final int A_OKAY = 0x59414b4f;
    private static final int A_CLSE = 0x45534c43;
    private static final int A_WRTE = 0x45545257;
    private static final int VERSION = 0x01000001;
    private static final int MAX = 256 * 1024;
    private static final byte[] BANNER = "host::\0".getBytes(StandardCharsets.UTF_8);

    private final PrivateKey privateKey;
    private final byte[] publicKey;
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private int nextLocal = 1;

    AdbLink(PrivateKey privateKey, byte[] publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    /**
     * One socket. If this key is new, the public key is sent once and this call stays on that
     * socket for up to 60 seconds so Allow can authorize it. A second connection is what made the
     * TV raise the RSA dialog over and over.
     */
    void connect(String host, int port) throws Exception {
        close();
        Socket next = new Socket();
        next.setTcpNoDelay(true);
        next.connect(new InetSocketAddress(host, port), 4000);
        next.setSoTimeout(8000);
        socket = next;
        in = next.getInputStream();
        out = next.getOutputStream();
        send(A_CNXN, VERSION, MAX, BANNER);
        boolean signed = false;
        boolean asked = false;
        while (true) {
            Msg msg;
            try {
                msg = read();
            } catch (java.net.SocketTimeoutException e) {
                if (asked) {
                    close();
                    throw new Unauthorized();
                }
                throw e;
            }
            if (msg.cmd == A_CNXN) return;
            if (msg.cmd == A_AUTH && msg.arg0 == 1 && !signed) {
                send(A_AUTH, 2, 0, sign(msg.data));
                signed = true;
                continue;
            }
            if (msg.cmd == A_AUTH && !asked) {
                send(A_AUTH, 3, 0, publicKey);
                asked = true;
                next.setSoTimeout(60_000);
                continue;
            }
        }
    }

    /** The Allow dialog was shown and nobody accepted it before the wait ended. */
    static final class Unauthorized extends IOException {
        Unauthorized() { super("unauthorized"); }
    }

    boolean isOpen() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    String shell(String command) throws Exception {
        if (!isOpen()) throw new IOException("Debugging is not connected.");
        int local = nextLocal++;
        byte[] payload = ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8);
        send(A_OPEN, local, 0, payload);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int remote = 0;
        for (int i = 0; i < 64; i++) {
            Msg msg = read();
            if (msg.arg1 != local && msg.cmd != A_OKAY) continue;
            if (msg.cmd == A_OKAY) {
                remote = msg.arg0;
            } else if (msg.cmd == A_WRTE) {
                body.write(msg.data);
                send(A_OKAY, local, msg.arg0, null);
            } else if (msg.cmd == A_CLSE) {
                if (remote != 0) send(A_CLSE, local, remote, null);
                return body.toString(StandardCharsets.UTF_8.name()).trim();
            }
        }
        throw new IOException("The command did not finish.");
    }

    void close() {
        Socket current = socket;
        socket = null;
        in = null;
        out = null;
        if (current != null) {
            try { current.close(); } catch (IOException ignored) { }
        }
    }

    /**
     * adbd checks a PKCS#1 signature of the SHA-1 DigestInfo. {@code NONEwithRSA} does not hash
     * the token again, and it does not need the Conscrypt key to be a CRT key.
     */
    private byte[] sign(byte[] token) throws Exception {
        if (token.length != 20) throw new IOException("Unexpected debugging token.");
        return AdbAuth.signToken(privateKey, token);
    }

    private void send(int cmd, int arg0, int arg1, byte[] data) throws IOException {
        byte[] payload = data == null ? new byte[0] : data;
        ByteBuffer header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(cmd);
        header.putInt(arg0);
        header.putInt(arg1);
        header.putInt(payload.length);
        header.putInt(checksum(payload));
        header.putInt(cmd ^ 0xffffffff);
        out.write(header.array());
        if (payload.length > 0) out.write(payload);
        out.flush();
    }

    private Msg read() throws IOException {
        byte[] header = readFully(24);
        ByteBuffer buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int cmd = buf.getInt();
        int arg0 = buf.getInt();
        int arg1 = buf.getInt();
        int len = buf.getInt();
        buf.getInt();
        int magic = buf.getInt();
        if (magic != (cmd ^ 0xffffffff) || len < 0 || len > MAX) throw new IOException("Bad debugging reply.");
        return new Msg(cmd, arg0, arg1, len == 0 ? new byte[0] : readFully(len));
    }

    private byte[] readFully(int len) throws IOException {
        byte[] buf = new byte[len];
        int n = 0;
        while (n < len) {
            int r = in.read(buf, n, len - n);
            if (r < 0) throw new IOException("Debugging closed the connection.");
            n += r;
        }
        return buf;
    }

    private static int checksum(byte[] data) {
        int sum = 0;
        for (byte b : data) sum += b & 0xff;
        return sum;
    }

    private static final class Msg {
        final int cmd, arg0, arg1;
        final byte[] data;
        Msg(int cmd, int arg0, int arg1, byte[] data) {
            this.cmd = cmd;
            this.arg0 = arg0;
            this.arg1 = arg1;
            this.data = data;
        }
    }
}
