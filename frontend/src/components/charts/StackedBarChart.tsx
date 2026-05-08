import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { ChartEmptyState, ChartLegend, ChartTooltip } from './ChartShared';

export type StackedSeries = { key: string; name: string; color: string };

export type StackedBarChartProps<T extends Record<string, number | string>> = {
  data: T[];
  xKey: string;
  series: StackedSeries[];
  height?: number;
  valueFormatter?: (v: number | string | undefined) => string;
  emptyMessage?: string;
};

export function StackedBarChart<T extends Record<string, number | string>>({
  data,
  xKey,
  series,
  height = 280,
  valueFormatter,
  emptyMessage,
}: StackedBarChartProps<T>) {
  if (!data || data.length === 0) {
    return <ChartEmptyState height={height} message={emptyMessage} />;
  }

  return (
    <div className="space-y-3">
      <ResponsiveContainer width="100%" height={height}>
        <BarChart
          data={data}
          margin={{ top: 8, right: 12, bottom: 0, left: -8 }}
          barCategoryGap={data.length === 1 ? '40%' : '22%'}
        >
          <CartesianGrid
            strokeDasharray="2 4"
            stroke="#e2e8f0"
            vertical={false}
          />
          <XAxis
            dataKey={xKey}
            tick={{ fontSize: 11, fill: '#64748b' }}
            tickLine={false}
            axisLine={{ stroke: '#e2e8f0' }}
            tickMargin={8}
          />
          <YAxis
            tick={{ fontSize: 11, fill: '#94a3b8' }}
            tickLine={false}
            axisLine={false}
            width={40}
            allowDecimals={false}
          />
          <Tooltip
            content={<ChartTooltip formatter={valueFormatter} />}
            cursor={{ fill: 'rgba(99, 102, 241, 0.06)' }}
          />
          {series.map((s, i) => (
            <Bar
              key={s.key}
              dataKey={s.key}
              name={s.name}
              stackId="a"
              fill={s.color}
              maxBarSize={42}
              radius={i === series.length - 1 ? [4, 4, 0, 0] : 0}
            />
          ))}
        </BarChart>
      </ResponsiveContainer>
      <ChartLegend series={series} className="px-1" />
    </div>
  );
}
