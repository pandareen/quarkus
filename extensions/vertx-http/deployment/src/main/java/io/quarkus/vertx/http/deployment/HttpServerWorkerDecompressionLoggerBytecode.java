package io.quarkus.vertx.http.deployment;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Replaces {@code new HttpContentDecompressor(false)} in Vert.x {@code HttpServerWorker#configureHttp1Pipeline}
 * with {@link io.quarkus.vertx.http.runtime.netty.LoggingHttpContentDecompressor} so decode failures are logged.
 */
final class HttpServerWorkerDecompressionLoggerBytecode {

    private static final String TARGET_METHOD = "configureHttp1Pipeline";
    private static final String TARGET_DESCRIPTOR = "(Lio/netty/channel/ChannelPipeline;)V";
    private static final String NETTY_DECOMPRESSOR = "io/netty/handler/codec/http/HttpContentDecompressor";
    private static final String QUARKUS_DECOMPRESSOR = "io/quarkus/vertx/http/runtime/netty/LoggingHttpContentDecompressor";

    private HttpServerWorkerDecompressionLoggerBytecode() {
    }

    static ClassVisitor create(ClassVisitor classVisitor) {
        return new ClassVisitor(Opcodes.ASM9, classVisitor) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (TARGET_METHOD.equals(name) && TARGET_DESCRIPTOR.equals(descriptor)) {
                    return new MethodVisitor(Opcodes.ASM9, delegate) {
                        @Override
                        public void visitTypeInsn(int opcode, String type) {
                            if (opcode == Opcodes.NEW && NETTY_DECOMPRESSOR.equals(type)) {
                                super.visitTypeInsn(opcode, QUARKUS_DECOMPRESSOR);
                            } else {
                                super.visitTypeInsn(opcode, type);
                            }
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
                                boolean isInterface) {
                            if (opcode == Opcodes.INVOKESPECIAL && "<init>".equals(name)
                                    && NETTY_DECOMPRESSOR.equals(owner) && "(Z)V".equals(descriptor)) {
                                super.visitMethodInsn(opcode, QUARKUS_DECOMPRESSOR, name, descriptor, isInterface);
                            } else {
                                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                            }
                        }
                    };
                }
                return delegate;
            }
        };
    }
}
