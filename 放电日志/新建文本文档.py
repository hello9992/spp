import re
from datetime import datetime, timedelta

def extract_discharge_data_by_10min(input_file, output_file):
    """
    从日志文件中提取每10分钟一行的放电数据，并保存为新的txt文件
    
    Args:
        input_file: 原始日志文件路径
        output_file: 生成的新文件路径
    """
    # 存储提取的放电数据
    discharge_data = []
    # 记录上一次提取数据的时间（用于判断是否间隔10分钟）
    last_extract_time = None
    
    # 正则表达式匹配放电数据行（格式：时:分:秒 RX: 电压mv 百分比 电量等级）
    pattern = re.compile(r'(\d{2}:\d{2}:\d{2})\s+RX:\s+(\d+)mv\s+(\d+)%\s+电量等级(\d+)')
    
    try:
        # 读取原始日志文件
        with open(input_file, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        # 遍历每一行数据
        for line in lines:
            match = pattern.match(line.strip())
            if match:
                # 解析时间和放电数据
                time_str = match.group(1)
                voltage = match.group(2)
                percentage = match.group(3)
                level = match.group(4)
                
                # 将时间字符串转换为datetime对象（方便计算时间差）
                current_time = datetime.strptime(time_str, '%H:%M:%S')
                
                # 首次提取 或 与上次提取时间间隔≥10分钟
                if last_extract_time is None or (current_time - last_extract_time) >= timedelta(minutes=10):
                    # 格式化数据行
                    data_line = f"{time_str} RX: {voltage}mv {percentage}% 电量等级{level}"
                    discharge_data.append(data_line)
                    # 更新上次提取时间
                    last_extract_time = current_time
        
        # 将提取的数据写入新的txt文件
        with open(output_file, 'w', encoding='utf-8') as f:
            # 写入标题
            f.write("### 设备C5:D8:1A:60:EB:BA放电数据（每10分钟提取）\n")
            # 写入数据行
            f.write('\n'.join(discharge_data))
        
        print(f"✅ 数据提取完成！新文件已保存至：{output_file}")
        print(f"📊 共提取到 {len(discharge_data)} 行10分钟间隔的放电数据")
        
    except FileNotFoundError:
        print(f"❌ 错误：找不到文件 {input_file}，请检查文件路径是否正确")
    except Exception as e:
        print(f"❌ 处理文件时出错：{str(e)}")

# ------------------- 执行脚本 -------------------
if __name__ == "__main__":
    # 请修改这里的文件路径（原始日志文件路径 和 生成的新文件路径）
    INPUT_FILE_PATH = "log_C5_D8_1A_60_EB_BA_1773964203569.txt"  # 原始日志文件
    OUTPUT_FILE_PATH = "discharge_data_10min.txt"  # 生成的每10分钟一行的放电数据文件
    
    # 调用函数提取数据并生成文件
    extract_discharge_data_by_10min(INPUT_FILE_PATH, OUTPUT_FILE_PATH)