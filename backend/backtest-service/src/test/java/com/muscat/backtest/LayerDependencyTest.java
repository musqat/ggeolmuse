package com.muscat.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.asm.AnnotationVisitor;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.FieldVisitor;
import org.springframework.asm.Handle;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.RecordComponentVisitor;
import org.springframework.asm.Type;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@DisplayName("계층 참조 규칙")
class LayerDependencyTest {

  private static final String BASE = "com/muscat/backtest/";

  @Test
  @DisplayName("domain 은 infra 를 참조하지 않음")
  void domainDoesNotUseInfra() throws IOException {
    assertThat(violations("domain", "infra")).isEmpty();
  }

  @Test
  @DisplayName("common 은 domain · infra 를 참조하지 않음")
  void commonDoesNotUseDomainOrInfra() throws IOException {
    assertThat(violations("common", "domain", "infra")).isEmpty();
  }

  @Test
  @DisplayName("검사는 본 코드 클래스를 읽고 반대 방향 참조(infra → domain)는 찾아냄")
  void scannerReadsClassesAndFindsReferences() throws IOException {
    assertThat(mainClasses("domain")).isNotEmpty();
    assertThat(mainClasses("common")).isNotEmpty();
    assertThat(violations("infra", "domain")).isNotEmpty();
  }

  // 본 코드(test-classes 뺌) layer 패키지 클래스 파일
  private static List<Resource> mainClasses(String layer) throws IOException {
    List<Resource> classes = new ArrayList<>();
    for (Resource resource : new PathMatchingResourcePatternResolver()
      .getResources("classpath*:" + BASE + layer + "/**/*.class")) {
      if (!resource.getURL().toString().contains("test-classes")) {
        classes.add(resource);
      }
    }
    return classes;
  }

  // 본 코드 layer 패키지 클래스가 참조하는 금지 패키지 타입("클래스 -> 참조 타입")
  private static List<String> violations(String layer, String... forbidden) throws IOException {
    List<String> found = new ArrayList<>();
    for (Resource resource : mainClasses(layer)) {
      try (InputStream in = resource.getInputStream()) {
        ClassReader reader = new ClassReader(in);
        for (String type : referencedTypes(reader)) {
          for (String target : forbidden) {
            if (type.startsWith(BASE + target + "/")) {
              found.add(reader.getClassName() + " -> " + type);
            }
          }
        }
      }
    }
    return found;
  }

  // 클래스 선언 · 필드 · 메서드 시그니처 · 본문 · 애너테이션이 참조하는 타입 내부 이름
  private static Set<String> referencedTypes(ClassReader reader) {
    Set<String> types = new TreeSet<>();
    AnnotationVisitor annotations = new AnnotationVisitor(Opcodes.ASM9) {
      @Override
      public void visit(String name, Object value) {
        if (value instanceof Type type) {
          addDescriptor(types, type.getDescriptor());
        }
      }

      @Override
      public void visitEnum(String name, String descriptor, String value) {
        addDescriptor(types, descriptor);
      }

      @Override
      public AnnotationVisitor visitAnnotation(String name, String descriptor) {
        addDescriptor(types, descriptor);
        return this;
      }

      @Override
      public AnnotationVisitor visitArray(String name) {
        return this;
      }
    };
    reader.accept(new ClassVisitor(Opcodes.ASM9) {
      @Override
      public void visit(int version, int access, String name, String signature, String superName,
        String[] interfaces) {
        addName(types, superName);
        if (interfaces != null) {
          for (String type : interfaces) {
            addName(types, type);
          }
        }
        addDescriptor(types, signature);
      }

      @Override
      public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        addDescriptor(types, descriptor);
        return annotations;
      }

      @Override
      public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
        addDescriptor(types, descriptor);
        addDescriptor(types, signature);
        return null;
      }

      @Override
      public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        addDescriptor(types, descriptor);
        addDescriptor(types, signature);
        return new FieldVisitor(Opcodes.ASM9) {
          @Override
          public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
            addDescriptor(types, annotation);
            return annotations;
          }
        };
      }

      @Override
      public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
        String[] exceptions) {
        addDescriptor(types, descriptor);
        addDescriptor(types, signature);
        if (exceptions != null) {
          for (String type : exceptions) {
            addName(types, type);
          }
        }
        return new MethodVisitor(Opcodes.ASM9) {
          @Override
          public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
            addDescriptor(types, annotation);
            return annotations;
          }

          @Override
          public AnnotationVisitor visitParameterAnnotation(int parameter, String annotation, boolean visible) {
            addDescriptor(types, annotation);
            return annotations;
          }

          @Override
          public void visitTypeInsn(int opcode, String type) {
            addName(types, type);
          }

          @Override
          public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDescriptor) {
            addName(types, owner);
            addDescriptor(types, fieldDescriptor);
          }

          @Override
          public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor,
            boolean isInterface) {
            addName(types, owner);
            addDescriptor(types, methodDescriptor);
          }

          @Override
          public void visitInvokeDynamicInsn(String methodName, String methodDescriptor, Handle bootstrap,
            Object... arguments) {
            addDescriptor(types, methodDescriptor);
            for (Object argument : arguments) {
              if (argument instanceof Handle handle) {
                addName(types, handle.getOwner());
                addDescriptor(types, handle.getDesc());
              } else if (argument instanceof Type type) {
                addDescriptor(types, type.getDescriptor());
              }
            }
          }

          @Override
          public void visitLdcInsn(Object value) {
            if (value instanceof Type type) {
              addDescriptor(types, type.getDescriptor());
            }
          }

          @Override
          public void visitMultiANewArrayInsn(String arrayDescriptor, int dimensions) {
            addDescriptor(types, arrayDescriptor);
          }

          @Override
          public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            addName(types, type);
          }

          @Override
          public void visitLocalVariable(String variable, String variableDescriptor, String signature, Label start,
            Label end, int index) {
            addDescriptor(types, variableDescriptor);
            addDescriptor(types, signature);
          }
        };
      }
    }, 0);
    return types;
  }

  private static final Pattern OBJECT_TYPE = Pattern.compile("L([^;<]+)");

  // descriptor · 제네릭 시그니처 안의 객체 타입
  private static void addDescriptor(Set<String> types, String descriptor) {
    if (descriptor == null) {
      return;
    }
    Matcher matcher = OBJECT_TYPE.matcher(descriptor);
    while (matcher.find()) {
      types.add(matcher.group(1));
    }
  }

  // 내부 이름(배열이면 원소 타입)
  private static void addName(Set<String> types, String name) {
    if (name == null) {
      return;
    }
    if (name.startsWith("[")) {
      addDescriptor(types, name);
    } else {
      types.add(name);
    }
  }
}
