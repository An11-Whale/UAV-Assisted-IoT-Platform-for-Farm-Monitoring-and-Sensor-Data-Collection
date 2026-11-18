import { sensorService } from '../../services/sensorService';
import type { SensorNode } from '../../types/sensor';

// Reads the backend's sensor directory for the dashboard.
// This is not network-level sensor discovery or node registration.
export async function listDashboardSensors(): Promise<SensorNode[]> {
  const { sensors } = await sensorService.getLatestSensors();
  return sensors;
}
