package com.netease.lottery.easymq;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hamcrest.core.IsInstanceOf;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.StringUtils;
import org.springframework.util.SystemPropertyUtils;

import com.google.common.collect.Sets;
import com.netease.lottery.easymq.handler.MQConsumerAnnotation;
import com.netease.lottery.easymq.handler.MQRecMsgHandler;

public class ScanPackageTest
{
	private final static Log log = LogFactory.getLog(ScanPackageTest.class);
	//扫描  scanPackages 下的文件的匹配符
	protected static final String DEFAULT_RESOURCE_PATTERN = "**/*.class";

	public static Set<String> findAnnotationClass(String scanPackages, Class<? extends Annotation> annotation)
	{
		//获取所有的类
		Set<String> fullClazzSet = findPackageClass(scanPackages);
		Set<String> clazzSet = Sets.newHashSet();
		for (String clazz : fullClazzSet)
		{
			try
			{
				if (findAnnotationClasses(clazz, annotation))
				{
					clazzSet.add(clazz);
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		return clazzSet;
	}

	/**
	 * 结合spring的类扫描方式
	 * 根据需要扫描的包路径及相应的注解，获取最终测method集合
	 * 仅返回public方法，如果方法是非public类型的，不会被返回
	 * 可以扫描工程下的class文件及jar中的class文件
	 *
	 * @param scanPackages
	 * @param annotation
	 * @return
	 */
	public static Set<Method> findClassAnnotationMethods(String scanPackages, Class<? extends Annotation> annotation)
	{
		//获取所有的类
		Set<String> clazzSet = findPackageClass(scanPackages);
		Set<Method> methods = new HashSet<Method>();
		//遍历类，查询相应的annotation方法
		for (String clazz : clazzSet)
		{
			try
			{
				Set<Method> ms = findAnnotationMethods(clazz, annotation);
				if (ms != null)
				{
					methods.addAll(ms);
				}
			}
			catch (ClassNotFoundException ignore)
			{
			}
		}
		return methods;
	}

	/**
	 * 根据扫描包的,查询下面的所有类
	 *
	 * @param scanPackages 扫描的package路径
	 * @return
	 */
	public static Set<String> findPackageClass(String scanPackages)
	{
		if (StringUtils.isEmpty(scanPackages))
		{
			return Collections.EMPTY_SET;
		}
		//验证及排重包路径,避免父子路径多次扫描
		Set<String> packages = checkPackage(scanPackages);
		ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
		MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resourcePatternResolver);
		Set<String> clazzSet = new HashSet<String>();
		for (String basePackage : packages)
		{
			if (StringUtils.isEmpty(basePackage))
			{
				continue;
			}
			String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
					+ org.springframework.util.ClassUtils.convertClassNameToResourcePath(SystemPropertyUtils
							.resolvePlaceholders(basePackage)) + "/" + DEFAULT_RESOURCE_PATTERN;
			try
			{
				Resource[] resources = resourcePatternResolver.getResources(packageSearchPath);
				for (Resource resource : resources)
				{
					//检查resource，这里的resource都是class
					String clazz = loadClassName(metadataReaderFactory, resource);
					clazzSet.add(clazz);
				}
			}
			catch (Exception e)
			{
				log.error("获取包下面的类信息失败,package:" + basePackage, e);
			}

		}
		return clazzSet;
	}

	/**
	 * 排重、检测package父子关系，避免多次扫描
	 *
	 * @param scanPackages
	 * @return 返回检查后有效的路径集合
	 */
	private static Set<String> checkPackage(String scanPackages)
	{
		if (StringUtils.isEmpty(scanPackages))
		{
			return Collections.EMPTY_SET;
		}
		Set<String> packages = new HashSet<String>();
		//排重路径
		Collections.addAll(packages, scanPackages.split(","));
		for (String pInArr : packages.toArray(new String[packages.size()]))
		{
			if (StringUtils.isEmpty(pInArr) || pInArr.equals(".") || pInArr.startsWith("."))
			{
				continue;
			}
			if (pInArr.endsWith("."))
			{
				pInArr = pInArr.substring(0, pInArr.length() - 1);
			}
			Iterator<String> packageIte = packages.iterator();
			boolean needAdd = true;
			while (packageIte.hasNext())
			{
				String pack = packageIte.next();
				if (pInArr.startsWith(pack + "."))
				{
					//如果待加入的路径是已经加入的pack的子集，不加入
					needAdd = false;
				}
				else if (pack.startsWith(pInArr + "."))
				{
					//如果待加入的路径是已经加入的pack的父集，删除已加入的pack
					packageIte.remove();
				}
			}
			if (needAdd)
			{
				packages.add(pInArr);
			}
		}
		return packages;
	}

	/**
	 * 加载资源，根据resource获取className
	 *
	 * @param metadataReaderFactory spring中用来读取resource为class的工具
	 * @param resource              这里的资源就是一个Class
	 * @throws IOException
	 */
	private static String loadClassName(MetadataReaderFactory metadataReaderFactory, Resource resource)
			throws IOException
	{
		try
		{
			if (resource.isReadable())
			{
				MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);
				if (metadataReader != null)
				{
					return metadataReader.getClassMetadata().getClassName();
				}
			}
		}
		catch (Exception e)
		{
			log.error("根据resource获取类名称失败", e);
		}
		return null;
	}

	/**
	 * 把action下面的所有method遍历一次，标记他们是否需要进行敏感词验证
	 * 如果需要，放入cache中
	 *
	 * @param fullClassName
	 */
	public static Set<Method> findAnnotationMethods(String fullClassName, Class<? extends Annotation> anno)
			throws ClassNotFoundException
	{
		Set<Method> methodSet = new HashSet<Method>();
		Class<?> clz = Class.forName(fullClassName);
		Method[] methods = clz.getDeclaredMethods();
		for (Method method : methods)
		{
			if (method.getModifiers() != Modifier.PUBLIC)
			{
				continue;
			}
			Annotation annotation = method.getAnnotation(anno);
			if (annotation != null)
			{
				methodSet.add(method);
			}
		}
		return methodSet;
	}

	public static boolean findAnnotationClasses(String fullClassName, Class<? extends Annotation> anno)
			throws ClassNotFoundException
	{
		Class<?> clz = Class.forName(fullClassName);
		Annotation annotation = clz.getAnnotation(anno);
		if (annotation != null)
		{
			System.out.println(fullClassName + " is true");
			return true;
		}
		return false;
	}

	public static void main(String[] args)
	{
		String packages = "com.netease.lottery.easymq";
		System.out.println("检测前的package: " + packages);
		System.out.println("annotation class:" + findAnnotationClass(packages, MQConsumerAnnotation.class));
	}
}