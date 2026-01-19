from flask import Flask, jsonify, request
import os
from pymongo import MongoClient
from bson import ObjectId
from flask_cors import CORS

app = Flask(__name__)
CORS(app)

mongo_uri = os.environ.get("MONGODB_URI")
if not mongo_uri:
    raise RuntimeError("MONGODB_URI is not set. Please set it as an environment variable.")
client = MongoClient(mongo_uri)

db = client["replay_db"]
sessions_col = db["sessions"]


@app.route("/health", methods=["GET"])
def health():
    try:
        sessions_col.count_documents({})
        return jsonify({"status": "ok", "db": "connected"}), 200
    except Exception as e:
        return jsonify({"status": "error", "db": "disconnected", "message": str(e)}), 500



@app.route("/sessions", methods=["POST"])
def create_session():
    data = request.get_json(force=True)

    result = sessions_col.insert_one(data)
    sid = str(result.inserted_id)

    sessions_col.update_one({"_id": result.inserted_id}, {"$set": {"sessionId": sid}})

    return jsonify({"sessionId": sid, "_id": sid}), 201



@app.route("/sessions/<session_id>", methods=["GET"])
def get_session(session_id):
    doc = None

    try:
        doc = sessions_col.find_one({"_id": ObjectId(session_id)})
    except Exception:
        pass

    if doc is None:
        doc = sessions_col.find_one({"sessionId": session_id})

    if doc is None:
        return jsonify({"error": "Session not found"}), 404

    doc["_id"] = str(doc["_id"])
    return jsonify(doc), 200

@app.route("/sessions", methods=["GET"])
def list_sessions():
    limit = int(request.args.get("limit", 20))
    docs = list(sessions_col.find({}, {"events": 0}).sort("_id", -1).limit(limit))
    for d in docs:
        d["_id"] = str(d["_id"])
    return jsonify({"sessions": docs, "count": len(docs)}), 200


@app.route("/sessions/<session_id>", methods=["PUT"])
def update_session(session_id):
    data = request.get_json(force=True)

    if not data:
        return jsonify({"error": "No data provided"}), 400

    # try update by ObjectId
    try:
        result = sessions_col.update_one(
            {"_id": ObjectId(session_id)},
            {"$set": data}
        )
        if result.matched_count == 1:
            return jsonify({"updated": True, "sessionId": session_id}), 200
    except Exception:
        pass

    # fallback update by sessionId
    result = sessions_col.update_one(
        {"sessionId": session_id},
        {"$set": data}
    )
    if result.matched_count == 1:
        return jsonify({"updated": True, "sessionId": session_id}), 200

    return jsonify({"updated": False, "error": "Session not found"}), 404




@app.route("/sessions/<session_id>", methods=["DELETE"])
def delete_session(session_id):
    # try by ObjectId
    try:
        result = sessions_col.delete_one({"_id": ObjectId(session_id)})
        if result.deleted_count == 1:
            return jsonify({"deleted": True, "sessionId": session_id}), 200
    except Exception:
        pass

    # fallback by sessionId
    result = sessions_col.delete_one({"sessionId": session_id})
    if result.deleted_count == 1:
        return jsonify({"deleted": True, "sessionId": session_id}), 200

    return jsonify({"deleted": False, "error": "Session not found"}), 404



if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
