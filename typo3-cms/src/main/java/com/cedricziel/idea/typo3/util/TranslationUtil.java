package com.cedricziel.idea.typo3.util;

import com.cedricziel.idea.typo3.index.TranslationIndex;
import com.cedricziel.idea.typo3.translation.TranslationLookupElement;
import com.cedricziel.idea.typo3.translation.TranslationReference;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.indexing.FileBasedIndex;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.intellij.psi.util.PsiModificationTracker.MODIFICATION_COUNT;

public class TranslationUtil {
    private static final Key<CachedValue<Collection<String>>> TRANSLATION_KEYS = new Key<>("TYPO3_CMS_TRANSLATION_KEYS");
    private static final ConcurrentMap<Project, Collection<String>> TRANSLATION_KEYS_LOCAL_CACHE = new ConcurrentHashMap<>();

    public static boolean keyExists(@NotNull Project project, @NotNull String key) {
        return getAllKeys(project).contains(key);
    }

    @NotNull
    private synchronized static Collection<String> getAllKeys(@NotNull Project project) {
        CachedValue<Collection<String>> cachedValue = project.getUserData(TRANSLATION_KEYS);
        if (cachedValue != null && cachedValue.hasUpToDateValue()) {
            return TRANSLATION_KEYS_LOCAL_CACHE.getOrDefault(project, new ArrayList<>());
        }

        cachedValue = CachedValuesManager.getManager(project).createCachedValue(() -> {
            Collection<String> allKeys = FileBasedIndex.getInstance().getAllKeys(TranslationIndex.KEY, project);
            if (TRANSLATION_KEYS_LOCAL_CACHE.containsKey(project)) {
                TRANSLATION_KEYS_LOCAL_CACHE.replace(project, allKeys);
            } else {
                TRANSLATION_KEYS_LOCAL_CACHE.put(project, allKeys);
            }

            return CachedValueProvider.Result.create(new ArrayList<>(), MODIFICATION_COUNT);
        }, false);

        project.putUserData(TRANSLATION_KEYS, cachedValue);

        return TRANSLATION_KEYS_LOCAL_CACHE.getOrDefault(project, cachedValue.getValue());
    }

    public static boolean isTranslationKeyString(@NotNull String possibleKey) {
        if (possibleKey.isEmpty()) {
            return false;
        }

        return possibleKey.startsWith("LLL:");
    }

    public static String extractResourceFilenameFromTranslationString(@NotNull String contents) {

        String[] split = contents.replace("LLL:EXT:", "").split("/");
        if (split.length > 0) {
            return split[0];
        }

        return null;
    }

    public static String extractTranslationKeyTranslationString(@NotNull String contents) {

        contents = contents.replace("LLL:", "");
        String[] split = contents.split(":");
        if (split.length > 0) {
            return split[1];
        }

        return null;
    }

    @NotNull
    public static TranslationLookupElement[] createLookupElements(@NotNull Project project) {
        return TranslationIndex
            .findAllTranslationStubs(project)
            .parallelStream()
            .map(TranslationLookupElement::new)
            .toArray(TranslationLookupElement[]::new);
    }

    public static PsiElement[] findDefinitionElements(@NotNull Project project, @NotNull String translationId) {
        Set<String> keys = new HashSet<>();
        keys.add(translationId);

        List<PsiElement> elements = new ArrayList<>();
        FileBasedIndex.getInstance().getFilesWithKey(TranslationIndex.KEY, keys, virtualFile -> {
            FileBasedIndex.getInstance().processValues(TranslationIndex.KEY, translationId, virtualFile, (file, value) -> {
                PsiFile file1 = PsiManager.getInstance(project).findFile(file);
                if (file1 != null) {
                    PsiElement elementAt = file1.findElementAt(value.getTextRange().getStartOffset());
                    if (elementAt != null) {
                        if (elementAt.getParent() instanceof XmlTag) {
                            XmlAttribute id = ((XmlTag) elementAt.getParent()).getAttribute("id");
                            if (id == null) {
                                return true;
                            }

                            elements.add(id.getValueElement());

                            return true;
                        }
                        elements.add(elementAt.getParent());
                    }
                }

                return true;
            }, GlobalSearchScope.allScope(project));

            return true;
        }, GlobalSearchScope.allScope(project));

        return elements.toArray(new PsiElement[elements.size()]);
    }

    public static boolean hasTranslationReference(@NotNull PsiElement element) {

        for (PsiReference reference : element.getReferences()) {
            if (reference instanceof TranslationReference) {
                return true;
            }
        }

        return false;
    }

    public static TranslationReference getTranslationReference(@NotNull PsiElement element) {

        for (PsiReference reference : element.getReferences()) {
            if (reference instanceof TranslationReference) {
                return (TranslationReference) reference;
            }
        }

        return null;
    }

    public static String keyFromReference(String key) {
        return StringUtils.substringAfterLast(key, ":");
    }
}
