package net.enthusia.staff.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

final class FrameTransport {
    private FrameTransport() {
    }

    static ProtocolEnvelope read(DataInputStream input, EnvelopeCodec codec) throws IOException {
        int length;
        try {
            length = input.readInt();
        } catch (EOFException exception) {
            throw exception;
        }
        if (length < 1 || length > EnvelopeCodec.MAX_FRAME_BYTES) {
            throw new IOException("invalid channel frame length");
        }
        return codec.decode(input.readNBytes(length));
    }

    static void write(DataOutputStream output, EnvelopeCodec codec, ProtocolEnvelope envelope) throws IOException {
        byte[] encoded = codec.encode(envelope);
        output.writeInt(encoded.length);
        output.write(encoded);
        output.flush();
    }
}
