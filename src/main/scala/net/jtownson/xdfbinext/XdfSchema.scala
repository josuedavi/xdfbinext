package net.jtownson.xdfbinext

import scala.collection.immutable.Seq

object XdfSchema:

  val undefinedAddress: Long = -1

  val floatingPointFlag = 0x10000
  val signedFlag        = 0x01

  case class XdfModel(version: String, xdfHeader: XdfHeader, tables: Seq[XdfTable]) {
    val tablesByName: Map[String, XdfTable] = tables.map(t => t.title -> t).toMap

    private val tablesByAddress: Map[Long, XdfTable] = tables.foldLeft(Map.empty[Long, XdfTable]) { (acc, nextTable) =>
      val zAddr = nextTable.axes.z.embeddedData.mmedAddress
      if (zAddr != undefinedAddress)
        acc.updated(zAddr, nextTable)
      else
        acc
    }

    private val isConstant: XdfTable => Boolean = table =>
      table.axes.x.embeddedData.mmedAddress == undefinedAddress &&
        table.axes.y.embeddedData.mmedAddress == undefinedAddress &&
        table.axes.x.indexCount == 1 &&
        table.axes.y.indexCount == 1

    private val is1D: XdfTable => Boolean = table => isRowMajorVector(table) || isColMajorVector(table)

    private def isRowMajorVector(t: XdfTable): Boolean =
      t.axes.x.indexCount == 1 && t.axes.y.indexCount > 1

    private def isColMajorVector(t: XdfTable): Boolean =
      t.axes.y.indexCount == 1 && t.axes.x.indexCount > 1

    private val is2D: XdfTable => Boolean = table =>
      (table.axes.x.embeddedData.mmedAddress != undefinedAddress || table.axes.x.indexCount > 1) &&
        (table.axes.y.embeddedData.mmedAddress != undefinedAddress || table.axes.y.indexCount > 1)

    val tablesConstant: Map[String, XdfTable] =
      tablesByName.filter((_, t) => isConstant(t))

    val tables1D: Map[String, Table1DEnriched] =
      tables.filter(is1D).map(table => table.title -> table1DAndBreakpoints(table.title)).toMap

    val tables2D: Map[String, Table2DEnriched] =
      tables.filter(is2D).map(table => table.title -> table2DAndBreakpoints(table.title)).toMap

    val breakPointTables: Set[String] = {
      val bp1D: Set[String] = tables1D.flatMap((_, t1d) => t1d.xAxisBreakpoints.map(_.title)).toSet
      val bp2D: Set[String] = tables2D
        .flatMap((_, t2d) => Seq(t2d.xAxisBreakpoints.map(_.title), t2d.yAxisBreakpoints.map(_.title)).flatten)
        .toSet
      bp1D ++ bp2D
    }

    def isBreakpointTable(xdfTable: XdfTable): Boolean = breakPointTables.contains(xdfTable.title)

    def table(tableName: String): XdfTable | Table1DEnriched | Table2DEnriched = {
      tablesConstant
        .get(tableName)
        .orElse(tables1D.get(tableName))
        .orElse(tables2D.get(tableName))
        .getOrElse(throw new IllegalArgumentException(s"Invalid table name $tableName"))
    }

    private def table2DAndBreakpoints(name: String): Table2DEnriched = {
      val tVal  = tablesByName(name)
      val xAddr = tVal.axes.x.embeddedData.mmedAddress
      val yAddr = tVal.axes.y.embeddedData.mmedAddress
      val tX    = if (xAddr == undefinedAddress) None else Some(tablesByAddress(xAddr))
      val tY    = if (yAddr == undefinedAddress) None else Some(tablesByAddress(yAddr))
      Table2DEnriched(tVal, tX, tY)
    }

    private def table1DAndBreakpoints(name: String): Table1DEnriched = {
      val tVal  = tablesByName(name)
      val xAddr = tVal.axes.x.embeddedData.mmedAddress
      val tX    = if (xAddr == undefinedAddress) None else Some(tablesByAddress(xAddr))

      Table1DEnriched(tVal, tX)
    }
  }

  case class Table2DEnriched(table: XdfTable, xAxisBreakpoints: Option[XdfTable], yAxisBreakpoints: Option[XdfTable])

  case class Table1DEnriched(table: XdfTable, xAxisBreakpoints: Option[XdfTable])

  case class XdfHeader(
      flags: Int,
      description: String,
      baseOffset: BaseOffset,
      defaults: Defaults,
      region: Region,
      categories: Seq[Category]
  )

  case class BaseOffset(offset: Int, subtract: Int)

  case class Defaults(
      dataSizeInBits: Int,
      sigDigits: Int,
      outputType: Int,
      signed: Int,
      lsbfirst: Int,
      float: Int
  )

  case class Region(
      `type`: Long,
      startAddress: Long,
      size: Long,
      regionColor: Int,
      regionFlags: Int,
      name: String,
      desc: String
  )

  case class Category(index: String, name: String)

  case class XdfTable(
      uniqueId: Int,
      flags: Int,
      title: String,
      description: String,
      categoryMems: Seq[CategoryMem],
      axes: Axes
  ) {
    def xLabels: Seq[String] = axes.x.labels.map(_.value)
    def yLabels: Seq[String] = axes.y.labels.map(_.value)

    def xUnits: String = axes.x.units
    def yUnits: String = axes.y.units
    def zUnits: String = axes.z.units
  }

  case class Axes(x: XdfAxisX, y: XdfAxisY, z: XdfAxisZ)

  case class CategoryMem(index: Int, category: Category)

  case class XdfAxisX(
      id: String,
      uniqueId: Int,
      embeddedData: EmbeddedData,
      indexCount: Int,
      dataType: Int,
      unitType: Int,
      daLink: DaLink,
      labels: Seq[Label],
      math: Math,
      units: String
  )

  case class XdfAxisY(
      id: String,
      uniqueId: Int,
      embeddedData: EmbeddedData,
      indexCount: Int,
      dataType: Int,
      unitType: Int,
      daLink: DaLink,
      labels: Seq[Label],
      math: Math,
      units: String
  )

  case class XdfAxisZ(
      id: String,
      embeddedData: EmbeddedData,
      decimalPl: Int,
      min: BigDecimal,
      max: BigDecimal,
      outputType: Int,
      math: Math,
      units: String
  )

  case class EmbeddedData(
      mmedTypeFlags: Int,
      mmedAddress: Long,
      mmedElementSizeBits: Int,
      mmedRowCount: Int,
      mmedColCount: Int,
      mmedMajorStrideBits: Int,
      mmedMinorStrideBits: Int
  )

  case class DaLink(index: Int)

  case class Label(index: Int, value: String)

  case class Math(equation: String, vars: Seq[Var])

  case class Var(id: String)
