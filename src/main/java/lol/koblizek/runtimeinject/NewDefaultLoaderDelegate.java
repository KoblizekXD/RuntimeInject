package lol.koblizek.runtimeinject;

import jdk.jshell.execution.LoaderDelegate;
import jdk.jshell.spi.ExecutionControl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.security.CodeSource;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class NewDefaultLoaderDelegate implements LoaderDelegate {

    private final NewDefaultLoaderDelegate.RemoteClassLoader loader;
    private final Map<String, Class<?>> klasses = new HashMap<>();

    public NewDefaultLoaderDelegate() {
        this.loader = new RemoteClassLoader(this.getClass().getClassLoader());
        Thread.currentThread().setContextClassLoader(loader);
    }

    private static class RemoteClassLoader extends URLClassLoader {

        private final Map<String, RemoteClassLoader.ClassFile> classFiles = new HashMap<>();

        RemoteClassLoader(ClassLoader parent) {
            super(new URL[0], parent);
        }

        private class ResourceURLStreamHandler extends URLStreamHandler {

            private final String name;

            ResourceURLStreamHandler(String name) {
                this.name = name;
            }

            @Override
            protected URLConnection openConnection(URL u) throws IOException {
                return new URLConnection(u) {
                    private InputStream in;
                    private Map<String, List<String>> fields;
                    private List<String> fieldNames;

                    @Override
                    public void connect() {
                        if (connected) {
                            return;
                        }
                        connected = true;
                        RemoteClassLoader.ClassFile file = classFiles.get(name);
                        in = new ByteArrayInputStream(file.data);
                        fields = new LinkedHashMap<>();
                        fields.put("content-length", List.of(Integer.toString(file.data.length)));
                        Instant instant = new Date(file.timestamp).toInstant();
                        ZonedDateTime time = ZonedDateTime.ofInstant(instant, ZoneId.of("GMT"));
                        String timeStamp = DateTimeFormatter.RFC_1123_DATE_TIME.format(time);
                        fields.put("date", List.of(timeStamp));
                        fields.put("last-modified", List.of(timeStamp));
                        fieldNames = new ArrayList<>(fields.keySet());
                    }

                    @Override
                    public InputStream getInputStream() throws IOException {
                        connect();
                        return in;
                    }

                    @Override
                    public String getHeaderField(String name) {
                        connect();
                        return fields.getOrDefault(name, List.of())
                                .stream()
                                .findFirst()
                                .orElse(null);
                    }

                    @Override
                    public Map<String, List<String>> getHeaderFields() {
                        connect();
                        return fields;
                    }

                    @Override
                    public String getHeaderFieldKey(int n) {
                        return n < fieldNames.size() ? fieldNames.get(n) : null;
                    }

                    @Override
                    public String getHeaderField(int n) {
                        String name = getHeaderFieldKey(n);

                        return name != null ? getHeaderField(name) : null;
                    }

                };
            }
        }

        void declare(String name, byte[] bytes) {
            classFiles.put(toResourceString(name), new RemoteClassLoader.ClassFile(bytes, System.currentTimeMillis()));
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            RemoteClassLoader.ClassFile file = classFiles.get(toResourceString(name));
            if (file == null) {
                return super.findClass(name);
            }
            return super.defineClass(name, file.data, 0, file.data.length, (CodeSource) null);
        }

        @Override
        public URL findResource(String name) {
            URL u = doFindResource(name);
            return u != null ? u : super.findResource(name);
        }

        @Override
        public Enumeration<URL> findResources(String name) throws IOException {
            URL u = doFindResource(name);
            Enumeration<URL> sup = super.findResources(name);

            if (u == null) {
                return sup;
            }

            List<URL> result = new ArrayList<>();

            while (sup.hasMoreElements()) {
                result.add(sup.nextElement());
            }

            result.add(u);

            return Collections.enumeration(result);
        }

        private URL doFindResource(String name) {
            if (classFiles.containsKey(name)) {
                try {
                    return new URL(null,
                            new URI("jshell", null, "/" + name, null).toString(),
                            new RemoteClassLoader.ResourceURLStreamHandler(name));
                } catch (MalformedURLException | URISyntaxException ex) {
                    throw new InternalError(ex);
                }
            }

            return null;
        }

        private String toResourceString(String className) {
            return className.replace('.', '/') + ".class";
        }

        @Override
        public void addURL(URL url) {
            super.addURL(url);
        }

        private static class ClassFile {
            public final byte[] data;
            public final long timestamp;

            ClassFile(byte[] data, long timestamp) {
                this.data = data;
                this.timestamp = timestamp;
            }

        }
    }

    @Override
    public void load(ExecutionControl.ClassBytecodes[] cbcs)
            throws ExecutionControl.ClassInstallException, ExecutionControl.EngineTerminationException {
        boolean[] loaded = new boolean[cbcs.length];
        try {
            for (ExecutionControl.ClassBytecodes cbc : cbcs) {
                loader.declare(cbc.name(), cbc.bytecodes());
            }
            for (int i = 0; i < cbcs.length; ++i) {
                ExecutionControl.ClassBytecodes cbc = cbcs[i];
                Class<?> klass = loader.loadClass(cbc.name());
                klasses.put(cbc.name(), klass);
                loaded[i] = true;
                // Get class loaded to the point of, at least, preparation
                klass.getDeclaredMethods();
            }
        } catch (Throwable ex) {
            throw new ExecutionControl.ClassInstallException("load: " + ex.getMessage(), loaded);
        }
    }

    @Override
    public void classesRedefined(ExecutionControl.ClassBytecodes[] cbcs) {
        for (ExecutionControl.ClassBytecodes cbc : cbcs) {
            loader.declare(cbc.name(), cbc.bytecodes());
        }
    }

    @Override
    public void addToClasspath(String cp)
            throws ExecutionControl.EngineTerminationException, ExecutionControl.InternalException {
        try {
            for (String path : cp.split(File.pathSeparator)) {
                loader.addURL(new File(path).toURI().toURL());
            }
        } catch (Exception ex) {
            throw new ExecutionControl.InternalException(ex.toString());
        }
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> klass = klasses.get(name);
        if (klass == null) {
            throw new ClassNotFoundException(name + " not found");
        } else {
            return klass;
        }
    }
}
