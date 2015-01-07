import static liveplugin.PluginUtil.*
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.BrowserUtil;

import java.io.*;


import static liveplugin.PluginUtil.*

class Stuff {
    def getProject(event) {
        return PlatformDataKeys.PROJECT.getData(event.getDataContext());
    }

    def getPsiFile(event)
    {
        return LangDataKeys.PSI_FILE.getData(event.getDataContext());
    }

    def getPsiElement(event)
    {
        return LangDataKeys.PSI_ELEMENT.getData(event.getDataContext());
    }

    def resolveClass(psiElement) {
        def CLASS_IMPL = "com.intellij.psi.impl.source.PsiClassImpl"
        def node = psiElement;
        while (!node.getClass().getName().equals(CLASS_IMPL)) {
            node = node.parent;
        }
        //show("Returning: "+ node.toString())
        return node;

    }
    def resolveMethod(elementAtCaret) {
        def node = elementAtCaret;
        def METHOD_IMPL = "com.intellij.psi.impl.source.PsiMethodImpl"
        while (!node.getClass().getName().equals(METHOD_IMPL)) {
            node = node.parent;
        }
        //show("Returning: "+ node.toString())
        return node;
    }

    def runTestMethod(project, baseDir, testClassName, testMethodName) {

        def console = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole()

        def pb = new ProcessBuilder("./gradlew",
                "testGithubUnittest",
                "--tests",
                testClassName + "." + testMethodName)
        pb.directory(new File(baseDir));
        def p = pb.start()
        try {
            def stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
            def stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

            def s = ""
            while ((s = stdInput.readLine()) != null) {
                console.print(s, ConsoleViewContentType.NORMAL_OUTPUT)
            }

            while ((s = stdError.readLine()) != null) {
                console.print(s, ConsoleViewContentType.ERROR_OUTPUT)
            }
        } catch (Exception e) {
            show("Got an exception: " + e)
        }
    }

    def runTest(event) {
        def editor = currentEditorIn(event.project)

        def psiFile = getPsiFile(event);
        def project = getProject(event);
        def basePath = ""
        def msg = "Editor: ["+editor+"]"
        msg += "\nFile:["+ psiFile + "]";
        msg += "\nProject: ["+project+"]";
        basePath = project.getBasePath().toString();

        def baseFileUrl = project.getBaseDir().toString()

        msg += "\nProject File Path: ["+basePath+"]";

        def elementAtCaret = psiFile.findElementAt(editor.getCaretModel().getOffset())

        def containingMethod = ""
        def containingClass = ""
        def testClassName = ""
        def testMethodName = ""
        if (elementAtCaret != null) {
            msg += "\nCurrent Element: ["+elementAtCaret+"]"
            containingMethod = resolveMethod(elementAtCaret)
            testMethodName = containingMethod.getName()
            if testMethodName
            msg += "\nCurrent method: ["+testMethodName+"]"

            containingClass = resolveClass(containingMethod)
            testClassName = containingClass.getQualifiedName();
            msg += "\nCurrent class: ["+testClassName+"]"

        } else {
            show("This doesn't look like a test method.")
            return;
        }

        //show("Project class: " + project.getClass())
        //show(msg)

        show("Testing "+ testClassName + ":" + testMethodName)
        runTestMethod(event.project, basePath, testClassName, testMethodName)
        BrowserUtil.open(baseFileUrl + "/android/build/test-report/github/unittest/classes/" + testClassName + ".html")
    }
}

registerAction("Run test method", "ctrl shift T", TOOLS_MENU){ AnActionEvent event ->
    new Stuff().runTest(event)
}


show("Loaded 'runTest'<br/>Use ctrl+shift+T to run it")
