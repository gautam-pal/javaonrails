package me.jor.classloader;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.SecureClassLoader;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.Lock;
import java.util.regex.Pattern;

import me.jor.util.LockCache;


public abstract class AbstractJORClassLoader extends SecureClassLoader{
	protected static final Pattern DOT_REGEX=Pattern.compile("\\.");
	private String startClassName;
	private boolean startClassInCustomPath;
	AbstractJORClassLoader(ClassLoader parent, String startClassName, boolean startClassInCustomPath){
		super(parent);
		this.startClassName=startClassName;
		this.startClassInCustomPath=startClassInCustomPath;
	}
	
	protected abstract InputStream getBytecodeInputStream(String name) throws MalformedURLException, ClassNotFoundException, IOException;
	protected abstract URL findJORResource(String name);
	
	private byte[] loadBytecode(InputStream in) throws IOException{
		ByteArrayOutputStream baos=null;
		try{
			baos=new ByteArrayOutputStream();
			byte[] b=new byte[1024];
			for(int r=0;;){
				r=in.read(b);
				if(r>=0){
					baos.write(b,0,r);
				}else{
					return baos.toByteArray();
				}
			}
		}finally{
			baos.close();
		}
	}
	private byte[] getBytecode(String name) throws IOException, ClassNotFoundException{
		InputStream in=null;
		if((!name.equals(startClassName)) || startClassInCustomPath){
			in=getBytecodeInputStream(name);
		}else{
			in=openLibInputStream(name);
		}
		if(in!=null){
			try{
				return loadBytecode(in);
			}finally{
				in.close();
			}
		}else{
			throw new NullPointerException("no class file was found");
		}
	}
	
	private boolean toLoad(Class c, String name){
		return c==null || (c.getClassLoader()!=this && name.equals(this.startClassName));
	}
	
	private Class<?> findJORClass(String name) throws IOException, ClassFormatError, ClassNotFoundException{
		Lock lock=LockCache.getReentrantLock("me.jor.classloader.JORClassLoader-"+name);
		try{
			lock.lock();
			Class c=super.findLoadedClass(name);
			if(toLoad(c,name)){
				byte[] bytecode=getBytecode(name);
				c=super.defineClass(name, bytecode, 0, bytecode.length);
			}
			return c;
		}finally{
			lock.unlock();
		}
	}
	
	@Override
	public Class<?> loadClass(String name)throws ClassNotFoundException{
		try{
			Class c=super.findLoadedClass(name);
			if(toLoad(c,name)){
				c=findJORClass(name);
			}
			return c;
		}catch(IOException e){
			return super.getParent().loadClass(name);
		}
	}
	
	protected String convertPackagePath(String className){
		return className.endsWith(".class")?className:DOT_REGEX.matcher(className).replaceAll("/")+".class";
	}
	
	protected InputStream openLibInputStream(String name) throws ClassNotFoundException, IOException{
		return super.getParent().getResourceAsStream(convertPackagePath(name));
	}
	
	@Override
	protected URL findResource(String name){
		if(!name.startsWith("/")){
			return findJORResource(name);
		}else{
			return null;
		}
	}
	@Override
	protected Enumeration<URL> findResources(String name){
		final URL url=findResource(name);
		if(url==null){
			return new Enumeration<URL>(){
				@Override
				public boolean hasMoreElements() {
					return false;
				}
				@Override
				public URL nextElement() {
					throw new NoSuchElementException();
				}
			};
		}else{
			return new Enumeration<URL>(){
				private boolean more=true;
				@Override
				public boolean hasMoreElements() {
					return more;
				}

				@Override
				public URL nextElement() {
					if(more){
						more=false;
						return url;
					}else{
						throw new NoSuchElementException();
					}
				}
			};
		}
	}
}
