/**
 * Copyright (c) 2020 Intel Corporation
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package build.model.gen;

import build.model.ast.ClassNode;
import build.model.ast.CollectionNode;
import build.model.ast.FieldNode;
import build.model.ast.GeneratedTypeNode;
import java.util.List;

public class InterfaceClassGenerator {

    private final CodeBuilder codeBuilder = new CodeBuilder();

    public InterfaceClassGenerator(
            String packageName, String[] imports, ClassNode classNode, List<ClassNode> otherClasses) {
        codeBuilder.appendLine("package " + packageName + ";");

        for (String imp : imports) {
            codeBuilder.appendLine("import " + imp + ";");
        }

        List<ClassNode> classChain = classNode.getClassChain(otherClasses);
        String[] additionalInterface = new String[0];

        if (classChain.size() > 1) {
            codeBuilder.append("public interface %s", NameHelper.getInterfaceTypeName(classNode));
            additionalInterface = new String[] {NameHelper.getInterfaceTypeName(classChain.get(classChain.size() - 2))};
        } else {
            codeBuilder.append("public interface %s", NameHelper.getInterfaceTypeName(classNode));
        }

        CommonCode.extendsInterfaces(
            codeBuilder, NameHelper.getInterfaceTypeName(classNode), classChain, additionalInterface);

        codeBuilder.indent().appendLines(new GettersGenerator(classNode).toString()).unindent().appendLine("}");
    }

    @Override
    public String toString() {
        return codeBuilder.toString();
    }

    private static class GettersGenerator {
        private final CodeBuilder codeBuilder = new CodeBuilder();

        GettersGenerator(ClassNode classNode) {
            for (FieldNode fieldNode : classNode.getFields()) {
                if (fieldNode.getType() instanceof CollectionNode) {
                    CollectionNode node = (CollectionNode)fieldNode.getType();
                    if (node.getGenericType() instanceof GeneratedTypeNode) {
                        codeBuilder.appendLine(
                            "%s<? extends %s> %s();",
                            node.getCollectionTypeName(),
                            NameHelper.getInterfaceTypeName(node.getGenericType()),
                            NameHelper.getGetterName(fieldNode));
                        continue;
                    }
                }

                if (fieldNode.getType() instanceof GeneratedTypeNode) {
                    codeBuilder.appendLine(
                        "%s %s();",
                        NameHelper.getInterfaceTypeName(fieldNode.getType()), NameHelper.getGetterName(fieldNode));
                } else {
                    codeBuilder.appendLine(
                        "%s %s();", fieldNode.getType().getTypeName(), NameHelper.getGetterName(fieldNode));
                }
            }
        }

        @Override
        public String toString() {
            return codeBuilder.toString();
        }
    }

}
