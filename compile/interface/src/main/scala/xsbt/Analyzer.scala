/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package xsbt

import scala.tools.nsc.{ io, plugins, symtab, Global, Phase }
import io.{ AbstractFile, PlainFile, ZipArchive }
import plugins.{ Plugin, PluginComponent }
import scala.collection.mutable.{ HashMap, HashSet, Map, Set }

import java.io.File
import java.util.zip.ZipFile
import xsbti.AnalysisCallback

object Analyzer {
  def name = "xsbt-analyzer"
}
final class Analyzer(val global: CallbackGlobal) extends LocateClassFile {
  import global._

  def newPhase(prev: Phase): Phase = new AnalyzerPhase(prev)
  private class AnalyzerPhase(prev: Phase) extends GlobalPhase(prev) {
    override def description = "Finds concrete instances of provided superclasses, and application entry points."
    def name = Analyzer.name
    def apply(unit: CompilationUnit) {
      if (!unit.isJava) {
        val sourceFile = unit.source.file.file
        // build list of generated classes
        for (iclass <- unit.icode) {
          val sym = iclass.symbol
          def addGenerated(separatorRequired: Boolean): Unit = {
            for (classFile <- outputDirs map (fileForClass(_, sym, separatorRequired)) find (_.exists)) {
              assert(sym.isClass, s"${sym.fullName} is not a class")
              val nonLocalClass = localToNonLocalClass(sym)
              if (sym == nonLocalClass) {
                val srcClassName = className(sym)
                val binaryClassName = flatclassName(sym, '.', separatorRequired)
                callback.generatedNonLocalClass(sourceFile, classFile, binaryClassName, srcClassName)
              } else {
                callback.generatedLocalClass(sourceFile, classFile)
              }
            }
          }
          if (sym.isModuleClass && !sym.isImplClass) {
            if (isTopLevelModule(sym) && sym.companionClass == NoSymbol)
              addGenerated(false)
            addGenerated(true)
          } else
            addGenerated(false)
        }
      }
    }
  }
}
