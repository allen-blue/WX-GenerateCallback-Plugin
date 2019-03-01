package com.mtime.android.base.wxplugin

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

class ClassVisitorAdapter extends ClassVisitor {

    static final String ANNOTATION_NAME0 = "Lcom/mtime/base/wx/annotation/WxCallback;"
    static final String ANNOTATION_NAME1 = "Lcom/mtime/base/wx/annotation/WxPayCallback;"
    OnCreateClass mCreateClass

    ClassVisitorAdapter(OnCreateClass createClass) {
        super(Opcodes.ASM6)
        mCreateClass = createClass
    }

    @Override
    AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (ANNOTATION_NAME0 == desc) {
            mCreateClass.createClass(0)
        } else if (ANNOTATION_NAME1 == desc) {
            mCreateClass.createClass(1)
        }
        return super.visitAnnotation(desc, visible)
    }

    static interface OnCreateClass {
        void createClass(int type)
    }
}
