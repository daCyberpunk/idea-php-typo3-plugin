package com.cedricziel.idea.typo3.userFunc;

import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlText;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.*;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class UserFuncReference extends PsiPolyVariantReferenceBase {
    private final String className;
    private final String methodName;

    public UserFuncReference(XmlText xmlText) {
        super(xmlText);

        String text = xmlText.getText();
        if (text.contains("->")) {
            String[] split = StringUtils.split(text, "->");

            switch (split.length) {
                case 0:
                    className = null;
                    methodName = null;
                    break;
                case 1:
                    className = split[0].trim();
                    methodName = null;
                    break;
                case 2:
                    className = split[0].trim();
                    methodName = split[1].trim();
                    break;
                default:
                    className = null;
                    methodName = null;
            }
        } else {
            className = null;
            methodName = text;
        }
    }

    public UserFuncReference(StringLiteralExpression stringLiteralExpression) {
        super(stringLiteralExpression);

        String text = stringLiteralExpression.getContents();
        if (text.contains("->")) {
            String[] split = StringUtils.split(text, "->");

            switch (split.length) {
                case 0:
                    className = null;
                    methodName = null;
                    break;
                case 1:
                    className = split[0].trim();
                    methodName = null;
                    break;
                case 2:
                    className = split[0].trim();
                    methodName = split[1].trim();
                    break;
                default:
                    className = null;
                    methodName = null;
            }
        } else {
            className = null;
            methodName = text;
        }
    }

    public UserFuncReference(ConcatenationExpression element) {
        super(element);

        ClassConstantReference classRef = PsiTreeUtil.findChildOfType(element, ClassConstantReference.class);
        StringLiteralExpression stringEl = PsiTreeUtil.findChildOfType(element, StringLiteralExpression.class);

        if (classRef == null || stringEl == null) {
            methodName = null;
            className = null;

            return;
        }

        className = classRef.getFQN();

        String[] split = StringUtils.split(stringEl.getContents(), "->");
        if (split.length == 0) {
            methodName = null;

            return;
        }

        methodName = split[0];
    }

    @NotNull
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        if (this.methodName == null) {

            return ResolveResult.EMPTY_ARRAY;
        }

        if (this.className == null) {
            Collection<Function> functionsByName = PhpIndex.getInstance(myElement.getProject()).getFunctionsByName(methodName);

            return PsiElementResolveResult.createResults(functionsByName);
        }

        String s = "#M#C\\" + this.className.replace("\\\\", "\\") + "." + this.methodName;
        Collection<? extends PhpNamedElement> bySignature = PhpIndex.getInstance(myElement.getProject()).getBySignature(s);

        return PsiElementResolveResult.createResults(bySignature);
    }

    @NotNull
    @Override
    public Object[] getVariants() {

        List<Object> list = new ArrayList<>();
        PhpIndex phpIndex = PhpIndex.getInstance(myElement.getProject());
        Iterator<String> count = phpIndex.getAllFunctionNames(null).iterator();

        while (true) {
            while (count.hasNext()) {
                String functionName = count.next();

                phpIndex
                        .getFunctionsByName(functionName)
                        .stream()
                        .map(UserFuncLookupElement::new)
                        .forEach(list::add);
            }

            break;
        }

        if (className != null) {
            phpIndex.getClassesByFQN(className).forEach(c -> {
                c.getMethods()
                        .stream()
                        .filter(method -> method.getAccess().isPublic())
                        .map(UserFuncLookupElement::new)
                        .forEach(list::add);
            });
        }

        Iterator<String> classes = phpIndex.getAllClassNames(null).iterator();
        while (true) {
            while (classes.hasNext()) {
                String className = classes.next();

                phpIndex
                        .getFunctionsByName(className)
                        .stream()
                        .map(UserFuncLookupElement::new)
                        .forEach(list::add);
            }

            break;
        }

        return list.toArray();
    }
}
