import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart, ScatterChart, GaugeChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  DataZoomComponent,
  MarkLineComponent,
  TitleComponent,
  ToolboxComponent,
} from 'echarts/components'

use([
  CanvasRenderer,
  LineChart,
  BarChart,
  ScatterChart,
  GaugeChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  DataZoomComponent,
  MarkLineComponent,
  TitleComponent,
  ToolboxComponent,
])
