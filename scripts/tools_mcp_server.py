"""
AI 助手 MCP 工具服务
提供天气查询和时间查询两个工具，通过 MCP 协议供 Spring AI 调用。

启动方式：
  pip install mcp requests
  python tools_mcp_server.py
"""

import asyncio
import json
import urllib.request
from datetime import datetime, timezone, timedelta

from mcp.server import Server, NotificationOptions
from mcp.server.models import InitializationOptions
import mcp.server.stdio
import mcp.types as types

server = Server("demo-tools")
CST = timezone(timedelta(hours=8))


@server.list_tools()
async def handle_list_tools() -> list[types.Tool]:
    return [
        types.Tool(
            name="get_weather",
            description="查询指定城市的实时天气，输入城市中文名称",
            inputSchema={
                "type": "object",
                "properties": {
                    "city": {
                        "type": "string",
                        "description": "城市中文名称，例如：杭州、北京、上海"
                    }
                },
                "required": ["city"]
            }
        ),
        types.Tool(
            name="get_current_time",
            description="获取当前的日期、时间和星期",
            inputSchema={
                "type": "object",
                "properties": {}
            }
        )
    ]


@server.call_tool()
async def handle_call_tool(name: str, arguments: dict) -> list[types.TextContent]:
    if name == "get_weather":
        city = arguments.get("city", "杭州")
        info = await fetch_weather(city)
        return [types.TextContent(type="text", text=info)]

    elif name == "get_current_time":
        now = datetime.now(CST)
        weekdays = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"]
        info = f"现在是 {now.strftime('%Y年%m月%d日')} {weekdays[now.weekday()]} {now.strftime('%H:%M:%S')}"
        return [types.TextContent(type="text", text=info)]

    raise ValueError(f"未知工具: {name}")


async def fetch_weather(city: str) -> str:
    """通过 Open-Meteo 免费 API 获取天气（需要先查经纬度）"""
    try:
        # 1. 城市名 → 经纬度（Open-Meteo Geocoding）
        geo_url = f"https://geocoding-api.open-meteo.com/v1/search?name={urllib.request.quote(city)}&count=1&language=zh"
        with urllib.request.urlopen(geo_url, timeout=5) as resp:
            geo_data = json.loads(resp.read().decode())
        if not geo_data.get("results"):
            return f"未找到城市「{city}」的天气信息"

        result = geo_data["results"][0]
        lat, lon = result["latitude"], result["longitude"]
        city_name = result.get("name", city)

        # 2. 经纬度 → 天气（Open-Meteo Weather）
        weather_url = (
            f"https://api.open-meteo.com/v1/forecast?"
            f"latitude={lat}&longitude={lon}"
            f"&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m"
            f"&timezone=Asia/Shanghai"
        )
        with urllib.request.urlopen(weather_url, timeout=5) as resp:
            weather_data = json.loads(resp.read().decode())

        current = weather_data["current"]
        temp = current["temperature_2m"]
        humidity = current["relative_humidity_2m"]
        wind = current["wind_speed_10m"]
        code = current["weather_code"]

        weather_desc = WEATHER_CODES.get(code, "未知")

        return (
            f"{city_name} 当前天气：{weather_desc}\n"
            f"温度：{temp}°C | 湿度：{humidity}% | 风速：{wind}km/h"
        )
    except Exception as e:
        return f"获取天气信息失败: {e}"


# WMO 天气码映射
WEATHER_CODES = {
    0: "晴天☀️", 1: "大部晴朗🌤️", 2: "多云⛅", 3: "阴天☁️",
    45: "有雾🌫️", 48: "雾凇", 51: "小毛毛雨🌧️", 53: "毛毛雨🌧️",
    55: "大毛毛雨🌧️", 61: "小雨🌧️", 63: "中雨🌧️", 65: "大雨🌧️",
    71: "小雪❄️", 73: "中雪❄️", 75: "大雪❄️", 80: "阵雨🌦️",
    95: "雷暴⛈️",
}


async def main():
    async with mcp.server.stdio.stdio_server() as (read_stream, write_stream):
        await server.run(
            read_stream, write_stream,
            InitializationOptions(
                server_name="demo-tools",
                server_version="1.0.0",
                capabilities=server.get_capabilities(
                    notification_options=NotificationOptions(),
                    experimental_capabilities={}
                )
            ),
        )


if __name__ == "__main__":
    asyncio.run(main())
