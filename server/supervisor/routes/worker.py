from typing import Dict, List

from fastapi import APIRouter, Depends

from supervisor.datas import Room, Worker, WorkerCreateBody
from supervisor.db import RedisClient, get_db
from supervisor.utils import catch_exceptions

worker_router = APIRouter()


@worker_router.get('/worker/get/all/', response_model=Dict[str, Worker])
@catch_exceptions
async def get_all_workers(db: RedisClient = Depends(get_db)):
    id_to_worker = await db.get_workers()
    return id_to_worker


@worker_router.get('/worker/{worker_id}/rooms/', response_model=List[Room])
@catch_exceptions
async def get_rooms_for_worker(worker_id: str, db: RedisClient = Depends(get_db)):
    id_to_room = await db.get_rooms()
    rooms = [room for room in id_to_room.values() if room.worker_id == worker_id]
    return rooms


@worker_router.post('/worker/modify/', response_model=List[Room])
@catch_exceptions
async def modify_workers(body: WorkerCreateBody, db: RedisClient = Depends(get_db)):
    id_to_worker = await db.get_workers()
    if body.id in id_to_worker:
        existing_worker = id_to_worker[body.id]
        existing_worker.id = body.id
        existing_worker.url = body.url
        existing_worker.capacity = body.capacity
        id_to_worker[body.id] = existing_worker
    else:
        id_to_worker[body.id] = Worker(id=body.id, url=body.url, capacity=body.capacity)
    await db.set_workers(id_to_worker)
