(ns leiningen.jpi
  (:require [clojure.java.io :as io]
            [leiningen.jar :as jar]
            [leiningen.core.classpath :as cp]
            [leiningen.core.project :as project]
            [leiningen.core.eval :as eval]
            [leiningen.core.main :as main])
  (:import (hudson Plugin)
           (clojure.asm Type Opcodes ClassWriter ClassVisitor)
           (clojure.asm.commons Method GeneratorAdapter)
           (java.util.jar Manifest JarEntry JarOutputStream)))

(defn generate-plugin-class [class-name  var-name]
  (let [internal-name (.replace class-name "." "/")
        cw (doto (ClassWriter. ClassWriter/COMPUTE_MAXS)
             (.visit Opcodes/V1_5
                     (+ Opcodes/ACC_PUBLIC
                        Opcodes/ACC_SUPER)
                     internal-name
                     nil
                     #_"java/lang/Object"
                     "hudson/Plugin"
                     nil))
        gen (doto (GeneratorAdapter. (+ Opcodes/ACC_PUBLIC
                                        Opcodes/ACC_STATIC)
                                     (Method/getMethod "void <clinit>()")
                                     nil nil cw)
              (.visitCode))]
    (doto (GeneratorAdapter. Opcodes/ACC_PUBLIC
                             (Method/getMethod "void <init>()")
                             nil nil cw)
      (.visitCode)
      (.loadThis)
      (.invokeConstructor (Type/getType Plugin)
                          (Method/getMethod "void <init>()"))
      (.returnValue)
      (.endMethod))
    (let [from (clojure.asm.Label.)
          to (clojure.asm.Label.)
          target (clojure.asm.Label.)
          end (clojure.asm.Label.)
          l (.newLocal gen (Type/getType ClassLoader))]
      (doto gen
        (.invokeStatic (Type/getType Thread)
                       (Method/getMethod "Thread currentThread()"))
        (.invokeVirtual (Type/getType Thread)
                        (Method/getMethod "ClassLoader getContextClassLoader()"))
        (.storeLocal l)
        (.invokeStatic (Type/getType Thread)
                       (Method/getMethod "Thread currentThread()"))
        (.push (Type/getType (str "L" internal-name ";")))
        (.invokeVirtual (Type/getType Class)
                        (Method/getMethod "ClassLoader getClassLoader()"))
        (.invokeVirtual (Type/getType Thread)
                        (Method/getMethod "void setContextClassLoader(ClassLoader)"))
        (.push "clojure.lang.RT")
        (.visitLabel from)
        (.invokeStatic (Type/getType Class)
                       (Method/getMethod "Class forName(String)"))
        (.push (doto (pr-str `(do (require (quote ~(symbol (namespace var-name))))
                                  (~var-name)))))
        (.invokeStatic (Type/getType clojure.lang.RT)
                       (Method/getMethod "Object readString(String)"))
        (.invokeStatic (Type/getType clojure.lang.Compiler)
                       (Method/getMethod "Object eval(Object)"))
        (.pop)
        (.visitLabel to)
        (.invokeStatic (Type/getType Thread)
                       (Method/getMethod "Thread currentThread()"))
        (.loadLocal l)
        (.invokeVirtual (Type/getType Thread)
                        (Method/getMethod "void setContextClassLoader(ClassLoader)"))
        (.goTo end)
        (.visitLabel target)
        (.invokeStatic (Type/getType Thread)
                       (Method/getMethod "Thread currentThread()"))
        (.loadLocal l)
        (.invokeVirtual (Type/getType Thread)
                        (Method/getMethod "void setContextClassLoader(ClassLoader)"))
        (.throwException)
        (.visitLabel end)
        (.visitTryCatchBlock from to target nil)
        (.returnValue)
        (.endMethod)))
    (.visitEnd cw)
    (.toByteArray cw)))

(defn jpi
  ""
  [project]
  (jar/jar project)
  (let [jar-file (#'jar/get-jar-filename project)
        class-name (munge
                    (str
                     (:group project)
                     "."
                     (:name project)))
        project (assoc project
                  :manifest (merge (:manifest project)
                                   {"Plugin-Class" class-name}))
        jpi-file (.replaceAll jar-file "\\.jar$" "\\.jpi")]
    (jar/write-jar
     project
     jpi-file
     (concat (for [f (cp/resolve-dependencies :dependencies project)]
               {:type :bytes
                :path (str "WEB-INF/lib/" (.getName f))
                :bytes (with-open [x (java.io.ByteArrayOutputStream.)]
                         (io/copy f x)
                         (.toByteArray x))})
             [{:type :bytes
               :path (str "WEB-INF/classes/"
                          (.replaceAll class-name "\\." "/") ".class")
               :bytes
               (generate-plugin-class class-name (:jenkins-inject project))}
              {:type :bytes
               :path (str "WEB-INF/lib/" (.getName (io/file jar-file)))
               :bytes (with-open [x (java.io.ByteArrayOutputStream.)]
                        (io/copy (io/file jar-file) x)
                        (.toByteArray x))}]))
    (main/info "Created" (str jpi-file))))
