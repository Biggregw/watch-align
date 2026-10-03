package com.watchalign.mobile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Repository files for source-level tests (Gradle runs unit tests in android/app). */
final class TestFiles {
    private TestFiles(){}

    static Path repoFile(String relative){
        Path d=Paths.get("").toAbsolutePath();
        for(int k=0;d!=null&&k<8;k++,d=d.getParent()){
            Path f=d.resolve(relative);
            if(Files.isRegularFile(f))return f;
        }
        throw new IllegalStateException("repository file not found: "+relative);
    }

    static String read(Path p)throws IOException{return new String(Files.readAllBytes(p),StandardCharsets.UTF_8);}
}
