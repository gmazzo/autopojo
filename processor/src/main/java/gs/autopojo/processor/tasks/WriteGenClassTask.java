package gs.autopojo.processor.tasks;

import com.squareup.javapoet.JavaFile;

import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.Callable;

import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;

import static gs.autopojo.processor.tasks.NamesHelper.getQualifiedName;

public class WriteGenClassTask implements Callable<JavaFileObject> {
    private final Filer filer;
    private final POJOClass pojoClass;

    public WriteGenClassTask(Filer filer, POJOClass pojoClass) {
        this.filer = filer;
        this.pojoClass = pojoClass;
    }

    @Override
    public JavaFileObject call() throws IOException {
        String fileName = getQualifiedName(pojoClass.name);

        JavaFileObject file = filer.createSourceFile(fileName);
        try (Writer wr = file.openWriter()) {
            JavaFile.builder(pojoClass.name.packageName(), pojoClass.typeSpec.build())
                    .build()
                    .writeTo(wr);
        }
        return file;
    }

}
