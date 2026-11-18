/** API client for latest sensor values and metric history, with mock-data fallback. */
import type { SensorNode, MetricHistoryPoint } from '../types/sensor';
import { MOCK_SENSORS } from '../mocks/mockSensors';

// Địa chỉ backend. Mặc định trỏ tới farm-sim (src/farm-sim) chạy ở cổng 8080;
// đổi bằng biến môi trường, ví dụ file .env:  VITE_API_BASE_URL=http://192.168.1.10:8080/api/v1
const API_BASE_URL: string =
  import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1';

// Nguồn dữ liệu của lần tải gần nhất: 'live' = backend/farm-sim, 'mock' = dữ liệu dự phòng
export type DataSource = 'live' | 'mock';

export const sensorService = {
  // 1. Lấy danh sách cảm biến và thông số mới nhất
  async getLatestSensors(): Promise<{ sensors: SensorNode[]; source: DataSource }> {
    try {
      const response = await fetch(`${API_BASE_URL}/sensors/latest`, {
        method: 'GET',
        headers: { 'Content-Type': 'application/json' },
        signal: AbortSignal.timeout(3000) // Timeout sau 3 giây theo đúng NFR
      });

      if (!response.ok) {
        throw new Error(`Lỗi máy chủ: ${response.status}`);
      }

      return { sensors: (await response.json()) as SensorNode[], source: 'live' };
    } catch (error) {
      console.warn("Chưa kết nối được Backend thật, đang dùng dữ liệu dự phòng:", error);
      // Chế độ dự phòng: Nếu backend chưa chạy, tự động trả về mock data để giao diện không bị chết
      return { sensors: MOCK_SENSORS, source: 'mock' };
    }
  },

  // 2. Lịch sử một chỉ số của một cảm biến (biểu đồ F06).
  // Trả về null khi backend không có dữ liệu cho cảm biến này -> giao diện dùng dữ liệu minh họa.
  async getHistory(
    sensorId: string,
    metric: string,
    range: '24h' | '7d'
  ): Promise<MetricHistoryPoint[] | null> {
    try {
      const url = `${API_BASE_URL}/sensors/${encodeURIComponent(sensorId)}/history`
        + `?metric=${encodeURIComponent(metric)}&range=${range}`;
      const response = await fetch(url, { signal: AbortSignal.timeout(3000) });
      if (!response.ok) return null;
      const payload: unknown = await response.json();
      if (!Array.isArray(payload)) return null;
      return payload.flatMap((item): MetricHistoryPoint[] => {
        const row = item as Record<string, unknown>;
        const value = Number(row?.value);
        return typeof row?.timestamp === 'string' && Number.isFinite(value)
          ? [{ timestamp: row.timestamp, value }]
          : [];
      });
    } catch {
      return null;
    }
  }
};
