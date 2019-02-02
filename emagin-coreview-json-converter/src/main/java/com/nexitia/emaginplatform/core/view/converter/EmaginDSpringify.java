/**
 *
 */
package com.nexitia.emaginplatform.core.view.converter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.atteo.xmlcombiner.XmlCombiner;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.nexitia.emaginplatform.core.commons.utils.StringUtils;
import com.nexitia.emaginplatform.core.ioc.api.annotations.ConvertViewToJson;
import com.nexitia.emaginplatform.core.ioc.api.annotations.CopyResource;
import com.nexitia.emaginplatform.core.ioc.api.annotations.CopyResources;
import com.nexitia.emaginplatform.core.ioc.api.annotations.GlobalComponents;
import com.nexitia.emaginplatform.core.ioc.api.annotations.GlobalComponentsToJson;
import com.nexitia.emaginplatform.core.ioc.api.annotations.I18n;
import com.nexitia.emaginplatform.core.ioc.api.annotations.View;
import com.nexitia.emaginplatform.jfx.core.api.ResourceUtils;
import com.nexitia.emaginplatform.jfx.core.client.viewdef.xml.model.VLViewConfigXML;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.impetus.annovention.ClasspathDiscoverer;
import com.impetus.annovention.Discoverer;
import com.impetus.annovention.listener.ClassAnnotationDiscoveryListener;

/**
 * Take source folder and target folder in argument.
 *
 * Will find all class annotated with convertToJson in classpath and generates associated bean and json files.
 * Will also copy resources to destination folde.
 *
 * Resulting in result project independent from spring framework as this last is not mobile and embedded aware.
 *
 * @author Ramilafananana  VONJISOA
 *
 */
public class EmaginDSpringify {

  private static boolean debug = false;
  protected static final String CONF_FILE_SUFFIX = ".xml";
  public static JAXBContext JC;

  private static boolean cleanDestination = true;

  static String DEST = null;
  static String SRC = null;

  static {
    try {
      JC = JAXBContext.newInstance(VLViewConfigXML.class);
      JC.createUnmarshaller();
    } catch (final JAXBException e) {
      e.printStackTrace();
    }
  }

  private static String extractFromArgs(String key, String[]args) {
    if(StringUtils.isEmpty(key)) {
      return null;
    }

    Iterator<String> it = Arrays.asList(args).iterator();
    while(it.hasNext()) {
      String token = it.next();
      if(token.equals(key) && it.hasNext()) {
        return it.next();
      }
    }

    return null;
  }

  private static void printUsage() {
    System.out.println();
    System.out.println("Emagin Mobile Extractor Usage : ");
    System.out.println("Each path should not be ended with '/' or '\' ");
    System.out.println("If path contains spaces, put it inside quote");
    System.out.println("java -jar XXXX -srcPath /path/to/src/folder -dstPath /path/to/dst/folder");
    System.out.println();
  }

  @SuppressWarnings("resource")
  public static void main(String[] args) throws IOException {

    SRC = StringUtils.removeEnd(extractFromArgs("-srcPath", args), File.separator);
    DEST = StringUtils.removeEnd(extractFromArgs("-dstPath", args), File.separator);

    if(StringUtils.isNotBlank(SRC) && StringUtils.isNotBlank(DEST)) {
      System.out.println("Process generation with following arguments?");
      System.out.println("Source folder : " + SRC);
      System.out.println("Destination folder : " + DEST);
      System.out.print("y/n :");
      System.out.flush();
      String val = new Scanner(System.in).next();

      if("y".equalsIgnoreCase(val)) {
        System.out.println("Processing ...");
      }
      else {
        System.out.println("ABORTED");
        printUsage();
        System.exit(-1);
      }
    }
    else {
      System.out.println("INVALID ARGUMENTS, ABORTING");
      printUsage();
      System.exit(-1);
    }


    // first clean destination
    clearDestination();

    // Scan for annotated class
    final Discoverer discoverer = new ClasspathDiscoverer();
    final ViewClassAnnotationListener listener = new ViewClassAnnotationListener();

    // Register class annotation listener
    discoverer.addAnnotationListener(listener);
    discoverer.discover(true, false, false, true, true);

    for (final Class<?> objectClass : listener.getClasses()) {
      final Method[] declaredMethods = objectClass.getDeclaredMethods();

      for (final Method method : declaredMethods) {
        if (method.isAnnotationPresent(View.class)) {

          // treat view
          View named = method.getAnnotation(View.class);

          Gson gson = new Gson();
          VLViewConfigXML config = getConfigurationFile(Arrays.asList(named.locations()));

          // name of file
          String filename = named.outputFileName();
          if(StringUtils.isEmpty(filename)) {
            String uniqueLocation = named.locations()[0];
            String fileName = org.apache.commons.lang.StringUtils.substringAfterLast(uniqueLocation, "/");
            filename = fileName.split("\\.")[0] + ".json";

            // System.out.println(filename);
          }

          String finalDest = null;
          String finalFolder = null;
          if(StringUtils.isEmpty(named.outputFilePath())){
            String packagename = org.apache.commons.lang.StringUtils.substringBeforeLast(named.locations()[0],"/");
            finalDest = DEST + "/" + packagename +  "/" + filename;
            finalFolder = DEST + "/" + packagename;
          }
          else {
            finalDest = DEST +  named.outputFilePath() + "/" + filename;
            finalFolder = DEST + named.outputFilePath() ;
          }

          File folder = new File(finalFolder);
          if(folder.exists()) {
            if(folder.isFile()) {

            }
          }
          else {
            folder.mkdirs();
          }

          try {
            String json = gson.toJson(config);
            System.out.println(json);
            com.google.common.io.Files.write(json.getBytes(), new File(finalDest));
          } catch (JsonIOException | IOException e) {
            System.out.println("ERROR " + finalDest);
            e.printStackTrace();
          }
        }

        // treat internatinoalisation
        if (method.isAnnotationPresent(I18n.class)) {
          I18n i18n = method.getAnnotation(I18n.class);
          String dest = i18n.dest();
          if(StringUtils.isNotBlank(dest)) {
            File destfile = new File(DEST + File.separator + dest);
            if(!destfile.exists()) {
              destfile.mkdirs();
            }

            if(destfile.isDirectory()) {
              String[] sources = i18n.locations();
              for(String source : sources) {
                File sourceFile = new File(SRC + File.separator + source);
                if(sourceFile.exists() && sourceFile.isFile()) {
                  Files.copy(sourceFile.toPath(),new File(destfile.toPath() +File.separator + sourceFile.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
              }
            }
          }
        }


        // treat @GlobalComponents
        if(method.isAnnotationPresent(GlobalComponents.class)) {
          GlobalComponents globalComponents = method.getAnnotation(GlobalComponents.class);
          System.out.println("XXXXXX --------");

          String[] sources =  globalComponents.source();
          String dest = globalComponents.destination();

          for(String s: sources) {
            if(s.startsWith("classpath:")) {
              try(InputStream is = EmaginDSpringify.class.getResourceAsStream(StringUtils.substringAfter(s, "classpath:"))){
                VLViewConfigXML finalResult = (VLViewConfigXML) JC.createUnmarshaller().unmarshal(is);

                String filename = getFilenameFrom(StringUtils.substringAfter(s, "classpath:")) + ".json";

                String finalFolder = DEST + "/" + dest ;
                File folder = new File(finalFolder);
                if(!folder.exists()) {
                  folder.mkdirs();
                }

                try {
                  Gson gson = new Gson();
                  String json = gson.toJson(finalResult);
                  System.out.println(json);
                  com.google.common.io.Files.write(json.getBytes(), new File(finalFolder + File.separator + filename));
                } catch (JsonIOException | IOException e) {
                  System.out.println("ERROR " + finalFolder);
                  e.printStackTrace();
                }
              } catch (JAXBException e) {
                e.printStackTrace();
              }
            }
          }
        }
      }
    }

    // treat @CopyResources
    for (final Class<?> objectClass : listener.getClasses()) {
      final Method[] declaredMethods = objectClass.getDeclaredMethods();

      for (final Method method : declaredMethods) {
        // treat @CopyResources
        if(method.isAnnotationPresent(CopyResource.class)) {
          CopyResource copyResource = method.getAnnotation(CopyResource.class);
          File sourceFile = new File(SRC + copyResource.source());
          String dest = copyResource.destination();
          File destfile = new File(DEST + dest);

          if(!destfile.exists()) {
            destfile.mkdirs();
          }

          Path newdir = destfile.toPath();
          Files.copy(sourceFile.toPath(), newdir.resolve(sourceFile.getName()), StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  private static void clearDestination() {
    if(cleanDestination) {
      File dest = new File(DEST);
      if(dest.isDirectory()) {
        File[] files = dest.listFiles();
        for(File f: files) {
          deleteFolder(f);
        }
      }
    }
  }

  private static void deleteFolder(File folder) {
    File[] files = folder.listFiles();
    if(files!=null) { //some JVMs return null for empty dirs
      for(File f: files) {
        if(f.isDirectory()) {
          deleteFolder(f);
        } else {
          f.delete();
        }
      }
    }

    folder.delete();
  }


  private static String getFilenameFrom(String path) {
    return org.apache.commons.lang.StringUtils.substringAfterLast(path, "/").split("\\.")[0];
  }


  public static VLViewConfigXML getConfigurationFile(List<String> confiFiles) {

    VLViewConfigXML finalResult = null;

    try {

      final XmlCombiner combiner = new XmlCombiner();
      final Map<String, String> env = new HashMap<>();
      env.put("create", "true");

      final boolean combine = confiFiles.size() > 0;
      for (final String uri : confiFiles) {
        System.out.println("Loading file : " + uri);
        InputStream is = ResourceUtils.getStream(EmaginDSpringify.class.getClass(), uri);
        if (is == null) {
          System.out.println("FATAL ERROR");
        }
        combiner.combine(is);
      }

      if (combine) {
        // LOG.debug(MessageFormat.format("Unmarshalling final view definition : {0}", controller.getId()));
        final Document result = combiner.buildDocument();
        finalResult = (VLViewConfigXML) JC.createUnmarshaller().unmarshal(result);

        if(debug) {
          try {
            final DOMSource domSource = new DOMSource(result);
            final StringWriter writer = new StringWriter();
            final StreamResult resultA = new StreamResult(writer);
            final TransformerFactory tf = TransformerFactory.newInstance();
            final Transformer transformer = tf.newTransformer();
            transformer.transform(domSource, resultA);
            System.out.println("XML IN String format is: \n" + writer.toString());
          } catch (final Exception e) {
            e.printStackTrace();
          }
        }
      } else {
        finalResult = new VLViewConfigXML();
      }
      // @formatter: off
    } catch (SAXException | IOException | TransformerFactoryConfigurationError | JAXBException | ParserConfigurationException e) {
      e.printStackTrace();
      // LOG.error(e);
    }
    // @formatter: on
    return finalResult;
  }

  /*-----------------------------------------------------------------------------
  | UTILITY CLASS FOR ANNOTATION DISCOVERING
   *=============================================================================*/
  /**
   * Utility for filtering classes to be scan for custom annotations discover.
   *
   * @author Administrator
   *
   */
  public  static class ViewClassAnnotationListener implements ClassAnnotationDiscoveryListener {

    private final Set<Class<?>> clazzes = new HashSet<>();

    /**
     * Constructor
     */
    public ViewClassAnnotationListener() {
    }

    /**
     * @return
     */
    public Set<Class<?>> getClasses() {
      return clazzes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void discovered(String clazz, String annotation) {
      try {
        clazzes.add(Class.forName(clazz));
      } catch (final ClassNotFoundException e) {
        e.printStackTrace();
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String[] supportedAnnotations() {
      return new String[] { ConvertViewToJson.class.getName(),
          GlobalComponentsToJson.class.getName(),
          CopyResources.class.getName()};
    }
  }
}
