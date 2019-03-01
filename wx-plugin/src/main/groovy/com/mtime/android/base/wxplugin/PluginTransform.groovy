package com.mtime.android.base.wxplugin

import com.android.build.api.transform.*
import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.pipeline.TransformManager
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

import java.util.jar.JarEntry
import java.util.jar.JarFile

class PluginTransform extends Transform implements ClassVisitorAdapter.OnCreateClass, Opcodes {
    static final String WX_CALLBACK_ACTIVITY = "com/mtime/base/share/wxapi/WXCallbackActivity"
    static final String WX_PAY_RESULT_ACTIVITY = "com/mtime/base/payment/WXPayResultActivity"

    Project mProject
    String mApplicationId
    TransformOutputProvider mOutputProvider

    PluginTransform(Project project) {
        mProject = project
    }

    @Override
    String getName() {
        return "MtimeWxTransform"
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    /**
     * 不支持增量编译
     * @return
     */
    @Override
    boolean isIncremental() {
        return false
    }

    @Override
    void transform(Context context,
                   Collection<TransformInput> inputs,
                   Collection<TransformInput> referencedInputs,
                   TransformOutputProvider outputProvider, boolean isIncremental)
            throws IOException, TransformException, InterruptedException {
        mOutputProvider = outputProvider

        def android = mProject.extensions.getByType(AppExtension)
        mApplicationId = android.defaultConfig.applicationId

        inputs.each { TransformInput input ->
            input.directoryInputs.each { DirectoryInput dirIn ->
                File dest = outputProvider.getContentLocation(dirIn.name, dirIn.contentTypes, dirIn.scopes, Format.DIRECTORY)
                filterClasses(dirIn.file)
                mProject.logger.info "Copying ${dirIn.name} to ${dest.absolutePath}"
                // 拷到目标文件
                FileUtils.copyDirectory(dirIn.file, dest)
            }

            input.jarInputs.each { JarInput jarIn ->
                String destName = jarIn.name
                // 重命名输出文件, 因为可能同名, 会覆盖
                def hexName = DigestUtils.md5Hex(jarIn.file.absolutePath)
                if (destName.endsWith(".jar")) {
                    destName = destName.substring(0, destName.length() - 4)
                }
                filterJars(jarIn.file)
                // 获得输出文件
                File dest = outputProvider.getContentLocation(destName + "_" + hexName, jarIn.contentTypes, jarIn.scopes, Format.JAR)

                mProject.logger.info "Copying ${jarIn.file.absolutePath} to ${dest.absolutePath}"
                FileUtils.copyFile(jarIn.file, dest)
            }
        }
    }

    void filterClasses(File inFile) {
        inFile.eachFileRecurse { File file ->
            if (file.isFile() && file.name.endsWith(".class")) {
                InputStream inputStream = new FileInputStream(file)
                handleStream(inputStream, file.name)
            }
        }
    }

    void filterJars(File jarFile) {
        def file = new JarFile(jarFile)
        Enumeration enumeration = file.entries()
        while (enumeration.hasMoreElements()) {
            JarEntry jarEntry = (JarEntry) enumeration.nextElement()
            InputStream inputStream = file.getInputStream(jarEntry)
            if (jarEntry.isDirectory()) {
                continue
            }
            handleStream(inputStream, jarEntry.name)
        }
        file.close()
    }

    void handleStream(InputStream inputStream, String name) {
        if (!name.endsWith(".class")) {
            return
        }
        try {
            ClassReader reader = new ClassReader(inputStream)
            inputStream.close()
            reader.accept(new ClassVisitorAdapter(this), 0)
        } catch (Exception e) {
            mProject.logger.error("parse class ${name} error", e)
        }
    }

    @Override
    void createClass(int type) {
        File dest = mOutputProvider.getContentLocation("mtimewxplugin", getInputTypes(), getScopes(), Format.DIRECTORY)

        String pkg = mApplicationId.replace(".", "/") + "/wxapi"
        File dir = new File(dest, pkg)
        dir.mkdirs()

        String name = type == 0 ? "WXEntryActivity" : "WXPayEntryActivity"
        File file = new File(dir, name + ".class")

        ClassWriter cw = new ClassWriter(0)
        String fullName = pkg + "/" + name
        String superName = type == 0 ? WX_CALLBACK_ACTIVITY : WX_PAY_RESULT_ACTIVITY

        cw.visit(V1_1, ACC_PUBLIC, fullName, null, superName, null)
        //生成默认的构造方法
        MethodVisitor mw = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null)
        //生成构造方法的字节码指令
        mw.visitVarInsn(ALOAD, 0)
        mw.visitMethodInsn(INVOKESPECIAL, superName, "<init>", "()V", false)
        mw.visitInsn(RETURN)
        mw.visitMaxs(1, 1)
        mw.visitEnd()

        FileOutputStream fos = new FileOutputStream(file)
        fos.write(cw.toByteArray())
        fos.close()
    }
}