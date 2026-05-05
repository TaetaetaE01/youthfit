import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

export type StackedSeries = { key: string; name: string; color: string };

export type StackedBarChartProps<T extends Record<string, number | string>> = {
  data: T[];
  xKey: string;
  series: StackedSeries[];
  height?: number;
};

export function StackedBarChart<T extends Record<string, number | string>>({
  data,
  xKey,
  series,
  height = 280,
}: StackedBarChartProps<T>) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data}>
        <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
        <XAxis dataKey={xKey} tick={{ fontSize: 12 }} />
        <YAxis tick={{ fontSize: 12 }} />
        <Tooltip />
        <Legend />
        {series.map((s) => (
          <Bar
            key={s.key}
            dataKey={s.key}
            name={s.name}
            stackId="a"
            fill={s.color}
          />
        ))}
      </BarChart>
    </ResponsiveContainer>
  );
}
