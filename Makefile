sysname = $(shell uname -o)
cygwinname = Cygwin
version = 6.10

.PHONY: build
build: compile jjsp

.PHONY: all
all: compile jjsp

.PHONY: compile
compile:
	mkdir -p build
	chmod -R a+r+w build
ifeq ($(sysname), $(cygwinname))
	javac --module-path "C:/Program Files/Java/javafx/lib;jjsplib" --add-modules ALL-MODULE-PATH -cp "." -parameters -d build `find src/ -name \*.java`
else
	javac --module-path "C:/Program Files/Java/javafx/lib:jjsplib" --add-modules ALL-MODULE-PATH -cp "." -parameters -d build `find src/ -name \*.java`
endif

.PHONY: clean
clean:
	rm -Rf build
	rm -f JJSP.jar
	rm -f `find src -iname \*.class`
	rm -f `find src -name \*~ -o -name \*#`

.PHONY: jjsp
jjsp: compile
	echo "Manifest-Version: 1.0" > jar.manifest
	echo "Name: JJSP" >> jar.manifest
	echo "Build-Date: " `date` >> jar.manifest
	echo "Specification-Title: JJSP" >> jar.manifest
	echo "Specification-Version: 3" >> jar.manifest
	echo "Implementation-Version: $(version)" >> jar.manifest
	echo "Main-Class: jjsp.engine.Launcher" >> jar.manifest
	echo "Git-Hash: " `git rev-parse HEAD` >> jar.manifest
	echo "" >> jar.manifest

	mkdir -p build/resources
	cp -r src/resources/* build/resources/
	chmod -R a+r+w build	

	jar -cfm JJSP.jar jar.manifest -C build /
	rm -f jar.manifest

.PHONY: doc
doc: compile
	echo "Manifest-Version: 1.0" > jar.manifest
	echo "Name: JJSP" >> jar.manifest
	echo "Build-Date: " `date` >> jar.manifest
	echo "Specification-Title: JJSP" >> jar.manifest
	echo "Specification-Version: 3" >> jar.manifest
	echo "Implementation-Version: $(version)" >> jar.manifest
	echo "Main-Class: jjsp.engine.Launcher" >> jar.manifest
	echo "Git-Hash: " `git rev-parse HEAD` >> jar.manifest
	echo "Readme: javadoc/Readme.html" >> jar.manifest
	echo "" >> jar.manifest

	mkdir -p build/resources
	cp -r src/resources/* build/resources/
	chmod -R a+r+w build	

	rm -rf doc/javadoc
	javadoc --module-path "C:/Program Files/Java/javafx/lib" --add-modules ALL-MODULE-PATH -cp ".;" -d doc/javadoc -sourcepath src jjsp.util jjsp.http jjsp.http.filters jjsp.engine 

	cp Readme.html doc/javadoc

	jar -cfm JJSP.jar jar.manifest -C build / -C doc .
	rm -f jar.manifest 

.PHONY: run
run:
	java --module-path 'C:/Program Files/Java/javafx/lib;jjsplib' --add-modules ALL-MODULE-PATH -cp "JJSP.jar" jjsp.engine.Launcher 

